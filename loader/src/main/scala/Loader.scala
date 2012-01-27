import com.healthmarketscience.jackcess
import java.io.File
import net.liftweb.json._
import net.liftweb.json.Serialization.write
import net.rfc1149.canape._
import scala.collection.JavaConversions._

// Usage: loader dbfile

object Loader extends App {

  implicit val formats = DefaultFormats

  val db = new NioCouch(auth = Some("admin", "admin")).db("steenwerck100km")

  val table = jackcess.Database.open(new File(args(0))).getTable("inscription")

  def get(id: String) = try { Some(db(id).execute) } catch { case StatusCode(404, _) => None }

  def capitalize(id: AnyRef) = id.asInstanceOf[String].toLowerCase.capitalize

  def fix(contestant: Map[String, AnyRef]) =
    contestant + ("nom" -> capitalize(contestant("nom"))) +
		 ("prenom" -> capitalize(contestant("prenom")))

  for (row <- table) {
    val id = "contestant-" + row("dossard")
    val newDoc = fix(row.toMap + ("_rev" -> get(id).map(_("_rev"))) + ("_id" -> id))
    println("Inserting bib %d (%s %s)".format(newDoc("dossard"), newDoc("prenom"), newDoc("nom")))
    db.insert(util.toJObject(newDoc)).execute
  }

  db.couch.releaseExternalResources
}
