import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import net.rfc1149.canape._
import org.apache.commons.dbcp2.BasicDataSource
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import play.api.libs.json._
import scopt.OptionParser

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps, reflectiveCalls}

// Usage: loader dbfile

object Loader extends App {

  import implicits._
  implicit val timeout: Duration = 1 minute

  private case class Options(year: Int = 0, host: Option[String] = None, port: Option[Int] = None,
    user: Option[String] = None, password: Option[String] = None,
    database: Option[String] = None, repeat: Option[Long] = None)

  private val parser = new OptionParser[Options]("loader") {
    help("help") text "show this help"
    opt[String]('h', "host") text "Mysql host" action { (x, c) ⇒ c.copy(host = Some(x)) }
    opt[Int]('P', "port") text "Mysql port" action { (x, c) ⇒ c.copy(port = Some(x)) }
    opt[String]('u', "user") text "Mysql user" action { (x, c) ⇒ c.copy(user = Some(x)) }
    opt[String]('p', "password") text "Mysql password" action { (x, c) ⇒ c.copy(password = Some(x)) }
    opt[String]('d', "database") text "Mysql database" action { (x, c) ⇒ c.copy(database = Some(x)) }
    opt[Long]('r', "repeat") text "Minutes between relaunching (default: do not relaunch)" action { (x, c) ⇒ c.copy(repeat = Some(x)) }
    arg[Int]("<year>") text "Year to import" action { (x, c) ⇒ c.copy(year = x) }
    override val showUsageOnError = true
  }

  private val options = parser.parse(args, Options()) getOrElse { sys.exit(1) }

  implicit class toCalendar(date: java.util.Date) {
    private val cal = Calendar.getInstance()
    cal.setTime(date)
    def get(i: Int) = cal.get(i)
  }

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer()
  val config = steenwerck.config.withFallback(ConfigFactory.load()).as[Config]("loader")
  val db = steenwerck.localCouch.db(steenwerck.localDbName)

  private def capitalize(name: String) = {
    val capitalized = "[ -]".r.split(name).map(_.toLowerCase.capitalize).mkString(" ")
    capitalized.zip(name) map {
      case (_, '-') ⇒ '-'
      case (c, _)   ⇒ c
    } mkString
  }

  private def fix(m: Map[String, Any]): JsObject = JsObject(m.toSeq map {
    case ("year", v: java.sql.Date)   ⇒ "year" → JsNumber(v.get(Calendar.YEAR))
    case (k, v: java.math.BigDecimal) ⇒ k → JsNumber(v.doubleValue())
    case (k, v: java.lang.Long)       ⇒ k → JsNumber(v.toLong)
    case (k, v: java.lang.Integer)    ⇒ k → JsNumber(v.toInt)
    case (k, v: java.util.Date)       ⇒ k → JsString(v.toString)
    case ("id", id: String)           ⇒ "mysql_id" → JsString(id)
    case (k, v: Boolean)              ⇒ k → JsBoolean(v)
    case (k, v: String)               ⇒ k → JsString(v)
  })

  private def containsAll(doc: JsObject, original: JsObject): Boolean = {
    doc.fields.forall {
      case (k, v) if original \ k == JsDefined(v) ⇒ true
      case _                                      ⇒ false
    }
  }

  try {

    val source = new BasicDataSource
    val host = options.host.orElse(config.as[Option[String]]("mysql-host")).getOrElse("localhost")
    val port = options.port.orElse(config.as[Option[Int]]("mysql-port")).getOrElse(3306)
    val database = options.database.getOrElse(config.getString("mysql-database"))
    source.setUrl(s"jdbc:mysql://$host:$port/$database?useSSL=true&verifyServerCertificate=false")
    options.user.orElse(config.as[Option[String]]("mysql-user")).foreach(source.setUsername)
    options.password.orElse(config.as[Option[String]]("mysql-password")).foreach(source.setPassword)

    do {
      val existing: Map[Long, JsObject] = db.view[Long, JsObject]("common", "all_contestants").execute().toMap
      println(s"Contestants already in the CouchDB database: ${existing.size}")

      val run = new QueryRunner(source)

      val teams = {
        val t = run.query(
          "SELECT * FROM teams WHERE year = ?",
          new MapListHandler,
          new java.lang.Integer(options.year)
        )
        (for (team ← t)
          yield team("id").asInstanceOf[java.lang.Integer] → team("name").asInstanceOf[String]).toMap
      }

      val upToDate = new AtomicInteger(0)
      val inserted = new AtomicInteger(0)
      val updated = new AtomicInteger(0)
      val q = run.query(
        "SELECT * FROM registrations WHERE year = ?",
        new MapListHandler,
        new java.lang.Integer(options.year)
      ).toList
      println(s"Starting checking/inserting/updating ${q.size} documents from MySQL")
      // Insertions/updates are grouped by a maximum of 20 at a time to ensure that the database will not
      // be overloaded and that we will encounter no timeouts.
      val dbops = Source(q).mapAsyncUnordered(20) { contestant ⇒
        val bib = contestant("bib").asInstanceOf[java.lang.Long]
        val id = "contestant-" + bib
        val firstName = capitalize(contestant("first_name").asInstanceOf[String])
        val name = contestant("name").asInstanceOf[String]
        val teamId = contestant("team_id").asInstanceOf[java.lang.Integer]
        val doc = fix(contestant.toMap.filterNot(_._2 == null)) ++
          Json.obj(
            "_id" → id,
            "type" → "contestant",
            "name" → name,
            "first_name" → firstName
          ) ++
            (if (teamId != null) Json.obj("team_name" → teams(teamId)) else Json.obj())
        val desc = s"bib $bib ($firstName $name)"
        existing.get(bib) match {
          case Some(original) ⇒
            if (containsAll(doc, original)) {
              upToDate.incrementAndGet()
              FastFuture.successful(bib)
            } else {
              db.insert(doc ++ Json.obj("_rev" → (original \ "_rev").get, "stalkers" → (original \ "stalkers").get)) andThen {
                case _ ⇒
                  println(s"Updated existing $desc")
                  updated.incrementAndGet()
              } recover {
                case t: Throwable ⇒
                  println(s"Could not update existing $desc: $t")
                  system.log.error(t, s"Could not update existing $desc")
                  Json.obj()
              } map { _ ⇒ bib }
            }
          case None ⇒
            db.insert(doc ++ Json.obj("stalkers" → Json.arr())) andThen {
              case _ ⇒
                println(s"Inserted $desc")
                inserted.incrementAndGet()
            } recover {
              case t: Throwable ⇒
                println(s"Could not insert $desc: $t")
                Json.obj()
            } map { _ ⇒ bib }
        }
      }.runFold(Set[Long]()) { case (set, bib) ⇒ set + bib }
      val bibs = Await.result(dbops, 1.minute)
      val removeops = Source(existing.keySet.diff(bibs)).mapAsyncUnordered(20) { bib ⇒
        val contestant = existing(bib)
        val firstName = (contestant \ "first_name").asOpt[String].getOrElse("John")
        val lastName = (contestant \ "name").asOpt[String].getOrElse("Doe")
        db.delete(contestant) map {
          case _ ⇒
            println(s"Remove contestant $bib ($firstName $lastName)")
            1
        } recover {
          case t: Throwable ⇒
            println(s"Could not remove contestant $bib ($firstName $lastName)")
            0
        }
      }.runFold(0)(_ + _)
      val removed = Await.result(removeops, 1.minute)
      println(s"Inserted documents: ${inserted.get()}")
      println(s"Updated documents: ${updated.get()}")
      println(s"Removed documents: $removed")
      println(s"Documents already up-to-date: ${upToDate.get()}")

      options.repeat.foreach { minutes ⇒
        println(s"Sleeping for $minutes minute${if (minutes > 1) "s" else ""}")
        Thread.sleep(minutes * 60000)
      }
    } while (options.repeat.isDefined)
  } finally {
    db.couch.releaseExternalResources().execute()
    system.terminate()
  }
}
