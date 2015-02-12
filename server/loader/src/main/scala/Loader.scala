import akka.actor.ActorSystem
import java.io.File
import java.util.Calendar
import net.liftweb.json._
import net.rfc1149.canape._
import org.apache.commons.dbcp.BasicDataSource
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import scala.collection.JavaConversions._
import scala.language.{implicitConversions, postfixOps, reflectiveCalls}
import scopt.OptionParser

// Usage: loader dbfile

object Loader extends App {

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

  implicit val formats = DefaultFormats

  implicit def toCalendar(date: java.util.Date) = new {
    private val cal = Calendar.getInstance()
    cal.setTime(date)
    def get(i: Int) = cal.get(i)
  }

  val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val db = new NioCouch(auth = Some("admin", "admin")).db("steenwerck100km")

  try {

    val options = parser.parse(args, Options()) getOrElse { sys.exit(1) }

    val source = new BasicDataSource
    source.setDriverClassName("com.mysql.jdbc.Driver")
    source.setUrl("jdbc:mysql://" + options.host + "/" + options.database)
    options.user.foreach(source.setUsername(_))
    options.password.foreach(source.setPassword(_))

    def get(id: String) = try { Some(db(id).execute()) } catch { case StatusCode(404, _) => None }

    val run = new QueryRunner(source)

    val teams = {
      val t = run.query("SELECT * FROM teams WHERE year = ?",
			new MapListHandler,
			new java.lang.Integer(options.year)).asInstanceOf[java.util.List[java.util.Map[java.lang.String, java.lang.Object]]]
      (for (team <- t)
         yield (team("id").asInstanceOf[java.lang.Integer] ->
		team("name").asInstanceOf[String])).toMap
    }

    val q = run.query("SELECT * FROM registrations WHERE year = ?",
		      new MapListHandler,
		      new java.lang.Integer(options.year)).asInstanceOf[java.util.List[java.util.Map[java.lang.String, java.lang.Object]]]
    for (contestant <- q) {
      val bib = contestant("bib").asInstanceOf[java.lang.Long]
      val id = "contestant-" + bib
      val firstName = capitalize(contestant("first_name").asInstanceOf[String])
      val name = contestant("name").asInstanceOf[String]
      val teamId = contestant("team_id").asInstanceOf[java.lang.Integer]
      val doc = fix(contestant.toMap.filterNot(_._2 == null)) ++
                Map("_id" -> id,
		    "type" -> "contestant",
		    "name" -> name,
		    "first_name" -> firstName) ++
		(if (teamId != null)
		  Map("team_name" -> teams(teamId))
		 else
		   Map.empty)
      val desc = "bib %d (%s %s)".format(bib, firstName, name)
      try {
	db.insert(util.toJObject(doc)).execute()
	println("Inserted " + desc)
      } catch {
	case StatusCode(409, _) =>
	  println("Updating existing " + desc)
	  db.insert(util.toJObject(doc + ("_rev" -> get(id).map(_("_rev"))))).execute()
      }
    }

    def fix(m: Map[String, AnyRef]) = m.map {
      case ("year", v: java.sql.Date) => "year" -> v.get(Calendar.YEAR)
      case (k, v: java.math.BigDecimal) => k -> v.doubleValue()
      case (k, v: java.util.Date) => k -> v.toString()
      case ("id", id) => ("mysql_id" -> id)
      case (k, v) => k -> v
    }

    def capitalize(name: String) = {
      val capitalized = "[ -]".r.split(name).map(_.toLowerCase.capitalize).mkString(" ")
      capitalized.zip(name) map { _ match {
	case (_, '-') => '-'
	case (c, _)   => c
      } } mkString
    }
  } finally {
    db.couch.releaseExternalResources()
    system.shutdown()
  }
}
