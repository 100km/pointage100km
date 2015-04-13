import java.util.Calendar

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
  implicit val timeout: Duration = (5, SECONDS)

  private case class Options(year: Int = 0, host: String = "localhost",
			     user: Option[String] = None, password: Option[String] = None,
			     database: String = "100km")

  private val parser = new OptionParser[Options]("loader") {
    help("help") text("show this help")
    opt[String]('h', "host") text("Mysql host ( default: localhost") action { (x, c) => c.copy(host = x) }
    opt[String]('u', "user") text("Mysql user") action { (x, c) => c.copy(user = Some(x)) }
    opt[String]('p', "password") text("Mysql password") action { (x, c) => c.copy(password = Some(x)) }
    opt[String]('d', "database") text("Mysql database (default: 100km") action { (x, c) => c.copy(database = x) }
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

  private def forceInsert(doc: JsObject): Future[_] =
    db((doc \ "_id").as[String]).flatMap { olderDoc => db.insert(doc + ("_rev" -> olderDoc \ "_rev")) }

  try {

    val options = parser.parse(args, Options()) getOrElse { sys.exit(1) }

    val source = new BasicDataSource
    source.setDriverClassName("com.mysql.jdbc.Driver")
    source.setUrl("jdbc:mysql://" + options.host + "/" + options.database)
    options.user.foreach(source.setUsername)
    options.password.foreach(source.setPassword)

    def get(id: String) = try { Some(db(id).execute()) } catch { case Couch.StatusError(404, _, _) => None }

    val run = new QueryRunner(source)

    val teams = {
      val t = run.query("SELECT * FROM teams WHERE year = ?",
			new MapListHandler,
			new java.lang.Integer(options.year))
      (for (team <- t)
        yield team("id").asInstanceOf[java.lang.Integer] -> team("name").asInstanceOf[String]).toMap
    }

    val q = run.query("SELECT * FROM registrations WHERE year = ?",
		      new MapListHandler,
		      new java.lang.Integer(options.year))
    for (contestant <- q) {
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
      try {
        db.insert(doc).execute()
        println("Inserted " + desc)
      } catch {
        case Couch.StatusError(409, _, _) =>
          println("Updating existing " + desc)
          forceInsert(doc).execute()
      }
    }

    def fix(m: Map[String, Any]): JsObject = JsObject(m.toSeq map {
      case ("year", v: java.sql.Date) => "year" -> JsNumber(v.get(Calendar.YEAR))
      case (k, v: java.math.BigDecimal) => k -> JsNumber(v.doubleValue())
      case (k, v: java.lang.Long) => k -> JsNumber(v.toLong)
      case (k, v: java.lang.Integer) => k -> JsNumber(v.toInt)
      case (k, v: java.util.Date) => k -> JsString(v.toString)
      case ("id", id: String) => "mysql_id" -> JsString(id)
      case (k, v: Boolean) => k -> JsBoolean(v)
      case (k, v: String) => k -> JsString(v)
    })

    def capitalize(name: String) = {
      val capitalized = "[ -]".r.split(name).map(_.toLowerCase.capitalize).mkString(" ")
      capitalized.zip(name) map {
        case (_, '-') => '-'
        case (c, _)   => c
      } mkString
    }
  } finally {
    db.couch.releaseExternalResources()
    system.shutdown()
  }
}
