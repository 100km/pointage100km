import akka.actor.ActorSystem
import java.io.File
import java.util.Calendar
import net.liftweb.json._
import net.rfc1149.canape._
import org.apache.commons.dbcp.BasicDataSource
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import scala.collection.JavaConversions._
import scopt.OptionParser

// Usage: loader dbfile

object Loader extends App {

  implicit val formats = DefaultFormats

  implicit def toCalendar(date: java.util.Date) = new {
    private val cal = Calendar.getInstance()
    cal.setTime(date)
    def get(i: Int) = cal.get(i)
  }

  val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  private object Options {
    var year: Int = _
    var host: Option[String] = None
    var user: Option[String] = None
    var password: Option[String] = None
    var database: Option[String] = None
  }

  private val parser = new OptionParser("loader") {
    help("H", "help", "show this help")
    opt("h", "host", "Mysql host", { h: String => Options.host = Some(h) })
    opt("u", "user", "Mysql user", { u: String => Options.user = Some(u) })
    opt("p", "password", "Mysql password", { p: String => Options.password = Some(p) })
    opt("d", "database", "Mysql database", { d: String => Options.database = Some(d) })
    arg("<year>", "Year to import", { y: String => Options.year = y.toInt })
  }

  val db = new NioCouch(auth = Some("admin", "admin")).db("steenwerck100km")

  try {

    if (!parser.parse(args)) {
      sys.exit(1)
    }

    val source = new BasicDataSource
    source.setDriverClassName("com.mysql.jdbc.Driver")
    source.setUrl("jdbc:mysql://" + Options.host.getOrElse("localhost") + "/" +
		  Options.database.getOrElse("100km"))
    Options.user.foreach(source.setUsername(_))
    Options.password.foreach(source.setPassword(_))

    def get(id: String) = try { Some(db(id).execute()) } catch { case StatusCode(404, _) => None }

    val run = new QueryRunner(source)
    val q = run.query("SELECT * FROM registrations WHERE year = ?",
		      new MapListHandler,
		      new java.lang.Integer(Options.year)).asInstanceOf[java.util.List[java.util.Map[java.lang.String, java.lang.Object]]]
    for (contestant <- q) {
      val bib = contestant("bib").asInstanceOf[java.lang.Long]
      val id = "contestant-" + bib
      val firstName = capitalize(contestant("first_name").asInstanceOf[String])
      val name = capitalize(contestant("name").asInstanceOf[String])
      val doc = fix(contestant.toMap.filterNot(_._2 == null)) ++
                Map("_id" -> id,
		    "type" -> "contestant",
		    "name" -> name,
		    "first_name" -> firstName)
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
