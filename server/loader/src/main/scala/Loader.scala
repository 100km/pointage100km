import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import net.rfc1149.canape._
import org.apache.commons.dbcp.BasicDataSource
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import play.api.libs.json._
import scopt.OptionParser

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps, reflectiveCalls}

// Usage: loader dbfile

object Loader extends App {

  import implicits._
  implicit val timeout: Duration = 1 minute

  private case class Options(year: Int = 0, host: String = "localhost",
			     user: Option[String] = None, password: Option[String] = None,
			     database: String = "100km", repeat: Option[Long] = None)

  private val parser = new OptionParser[Options]("loader") {
    help("help") text("show this help")
    opt[String]('h', "host") text("Mysql host (default: localhost)") action { (x, c) => c.copy(host = x) }
    opt[String]('u', "user") text("Mysql user") action { (x, c) => c.copy(user = Some(x)) }
    opt[String]('p', "password") text("Mysql password") action { (x, c) => c.copy(password = Some(x)) }
    opt[String]('d', "database") text("Mysql database (default: 100km") action { (x, c) => c.copy(database = x) }
    opt[Long]('r', "repeat") text("Minutes between relaunching (default: do not relaunch)") action { (x, c) => c.copy(repeat = Some(x)) }
    arg[Int]("<year>") text("Year to import") action { (x, c) => c.copy(year = x) }
  }

  implicit class toCalendar(date: java.util.Date) {
    private val cal = Calendar.getInstance()
    cal.setTime(date)
    def get(i: Int) = cal.get(i)
  }

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val db = steenwerck.localCouch.db(steenwerck.localDbName)

  private def capitalize(name: String) = {
    val capitalized = "[ -]".r.split(name).map(_.toLowerCase.capitalize).mkString(" ")
    capitalized.zip(name) map {
      case (_, '-') => '-'
      case (c, _)   => c
    } mkString
  }

  private def fix(m: Map[String, Any]): JsObject = JsObject(m.toSeq map {
    case ("year", v: java.sql.Date) => "year" -> JsNumber(v.get(Calendar.YEAR))
    case (k, v: java.math.BigDecimal) => k -> JsNumber(v.doubleValue())
    case (k, v: java.lang.Long) => k -> JsNumber(v.toLong)
    case (k, v: java.lang.Integer) => k -> JsNumber(v.toInt)
    case (k, v: java.util.Date) => k -> JsString(v.toString)
    case ("id", id: String) => "mysql_id" -> JsString(id)
    case (k, v: Boolean) => k -> JsBoolean(v)
    case (k, v: String) => k -> JsString(v)
  })

  private def containsAll(doc: JsObject, original: JsObject): Boolean = {
    doc.fields.forall {
      case (k, v) if original \ k == v => true
      case _ => false
    }
  }

  private def forceInsert(doc: JsObject): Future[JsValue] =
    db((doc \ "_id").as[String]).flatMap { olderDoc => db.insert(doc + ("_rev" -> (olderDoc \ "_rev").get)) }

  try {

    val options = parser.parse(args, Options()) getOrElse { sys.exit(1) }

    val source = new BasicDataSource
    source.setDriverClassName("com.mysql.jdbc.Driver")
    source.setUrl("jdbc:mysql://" + options.host + "/" + options.database)
    options.user.foreach(source.setUsername)
    options.password.foreach(source.setPassword)

    do {
      val existing: Map[Long, JsObject] = db.view[Long, JsObject]("common", "all_contestants").execute().toMap
      println(s"Contestants already in the CouchDB database: ${existing.size}")

      val run = new QueryRunner(source)

      val teams = {
        val t = run.query("SELECT * FROM teams WHERE year = ?",
          new MapListHandler,
          new java.lang.Integer(options.year))
        (for (team <- t)
          yield team("id").asInstanceOf[java.lang.Integer] -> team("name").asInstanceOf[String]).toMap
      }

      val upToDate = new AtomicInteger(0)
      val inserted = new AtomicInteger(0)
      val updated = new AtomicInteger(0)
      val q = run.query("SELECT * FROM registrations WHERE year = ?",
        new MapListHandler,
        new java.lang.Integer(options.year))
      println(s"Starting checking/inserting/updating ${q.size} documents from MySQL")
      // Insertions/updates are grouped by a maximum of 20 at a time to ensure that the database will not
      // be overloaded and that we will encounter no timeouts.
      for (r <- q.grouped(20)) {
        val future = Future.traverse(r) { contestant =>
          val bib = contestant("bib").asInstanceOf[java.lang.Long]
          val id = "contestant-" + bib
          val firstName = capitalize(contestant("first_name").asInstanceOf[String])
          val name = contestant("name").asInstanceOf[String]
          val teamId = contestant("team_id").asInstanceOf[java.lang.Integer]
          val doc = fix(contestant.toMap.filterNot(_._2 == null)) ++
            Json.obj("_id" -> id,
              "type" -> "contestant",
              "name" -> name,
              "first_name" -> firstName) ++
            (if (teamId != null) Json.obj("team_name" -> teams(teamId)) else Json.obj())
          val desc = s"bib $bib ($firstName $name)"
          existing.get(bib) match {
            case Some(original) =>
              if (containsAll(doc, original)) {
                upToDate.incrementAndGet()
                Future.successful(Json.obj())
              } else {
                db.insert(doc ++ Json.obj("_rev" -> (original \ "_rev").get, "stalkers" -> (original \ "stalkers").get)) andThen {
                  case _ =>
                    println(s"Updated existing $desc")
                    updated.incrementAndGet()
                } recoverWith {
                  case t: Throwable =>
                    println(s"Could not update existing $desc: $t")
                    Future.successful(Json.obj())
                }
              }
            case None =>
              db.insert(doc ++ Json.obj("stalkers" -> Json.arr())) andThen { case _ =>
                println(s"Inserted $desc")
                inserted.incrementAndGet()
              } recoverWith {
                case t: Throwable =>
                  println(s"Could not insert $desc: $t")
                  Future.successful(Json.obj())
              }
          }
        }
        future.execute()
      }
      println(s"Inserted documents: ${inserted.get()}")
      println(s"Updated documents: ${updated.get()}")
      println(s"Documents already up-to-date: ${upToDate.get()}")

      options.repeat.foreach { minutes =>
        println(s"Sleeping for $minutes minute${if (minutes > 1) "s" else ""}")
        Thread.sleep(minutes * 60000)
      }
    } while (options.repeat.isDefined)
  } finally {
    db.couch.releaseExternalResources().execute()
    system.terminate()
  }
}
