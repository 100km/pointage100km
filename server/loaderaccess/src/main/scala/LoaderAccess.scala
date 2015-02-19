import akka.actor.ActorSystem
import com.healthmarketscience.jackcess
import java.io.File
import net.liftweb.json._
import net.rfc1149.canape._
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.language.postfixOps
import scopt.OptionParser

// Usage: loaderaccess dbfile

object LoaderAccess extends App {

  import implicits._
  implicit val timeout: Duration = (5, SECONDS)

  private object Options {
    var file: File = _
  }

  private val parser = new OptionParser[File]("loaderaccess") {
    help("help") abbr("h") text("show this help")
    arg[String]("<database>") text("MS access database to import") action ((x, c) => new File(x))
  }

  private val filename = parser.parse(args, null) getOrElse { sys.exit(1) }

  implicit val formats = DefaultFormats

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val table = jackcess.Database.open(filename, true).getTable("inscription")

  val db = new Couch(auth = Some("admin", "admin")).db("steenwerck100km")

  val format = new java.text.SimpleDateFormat("yyyy/MM/dd")

  def get(id: String) = try { Some(db(id).execute()) } catch { case Couch.StatusError(404, _) => None }

  def capitalize(name: String) = {
    val capitalized = "[ -]".r.split(name).map(_.toLowerCase.capitalize).mkString(" ")
    capitalized.zip(name) map { _ match {
      case (_, '-') => '-'
      case (c, _)   => c
    } } mkString
  }

  def fix(contestant: Map[String, AnyRef]) =
    contestant map {
      case ("nom", v: String) => "nom" -> capitalize(v)
      case ("prenom", v: String) => "prenom" -> capitalize(v)
      case (k, v: java.util.Date) => k -> (if (v == null) null else format.format(v))
      case (k, v: java.math.BigDecimal) => k -> v.doubleValue()
      case (k, v) => k -> v
    }

  for (row <- table) {
    val id = "contestant-" + row("dossard")
    val doc = fix(row.toMap + ("_id" -> id) + ("type" -> "contestant"))
    val desc = "bib %d (%s %s)".format(doc("dossard"), doc("prenom"), doc("nom"))
    try {
      db.insert(util.toJObject(doc)).execute()
      println("Inserted " + desc)
    } catch {
	case Couch.StatusError(409, _) =>
	  println("Updating existing " + desc)
	  db.insert(util.toJObject(doc + ("_rev" -> get(id).map(_("_rev"))))).execute()
    }
  }

  db.couch.releaseExternalResources()
  system.shutdown()
}
