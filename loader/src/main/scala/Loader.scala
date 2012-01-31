import com.healthmarketscience.jackcess
import java.io.File
import net.liftweb.json._
import net.liftweb.json.Serialization.write
import net.rfc1149.canape._
import scala.collection.JavaConversions._
import scopt.OptionParser

// Usage: loader dbfile

object Loader extends App {

  implicit val formats = DefaultFormats

  private object Options {
    var file: File = _
  }

  private val parser = new OptionParser("loader") {
    help("h", "help", "show this help")
    arg("database", "MS access database to import", { s: String => Options.file = new File(s) })
  }

  if (!parser.parse(args))
    sys.exit(1)

  val table = jackcess.Database.open(Options.file).getTable("inscription")

  val db = new NioCouch(auth = Some("admin", "admin")).db("steenwerck100km")

  def get(id: String) = try { Some(db(id).execute) } catch { case StatusCode(404, _) => None }

  def capitalize(name: String) = {
    val capitalized = "[ -]".r.split(name).map(_.toLowerCase.capitalize).mkString(" ")
    capitalized.zip(name) map { _ match {
      case (_, '-') => '-'
      case (c, _)   => c
    } } mkString
  }

  def fix(contestant: Map[String, AnyRef]) =
    contestant + ("nom" -> capitalize(contestant("nom").asInstanceOf[String])) +
		 ("prenom" -> capitalize(contestant("prenom").asInstanceOf[String]))

  for (row <- table) {
    val id = "contestant-" + row("dossard")
    val newDoc = fix(row.toMap + ("_rev" -> get(id).map(_("_rev"))) + ("_id" -> id))
    println("Inserting bib %d (%s %s)".format(newDoc("dossard"), newDoc("prenom"), newDoc("nom")))
    db.insert(util.toJObject(newDoc)).execute
  }

  db.couch.releaseExternalResources
}
