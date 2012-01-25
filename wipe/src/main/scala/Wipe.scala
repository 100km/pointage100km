import net.liftweb.json._
import net.rfc1149.canape._

// Usage: wipe login password
// Wipe everything on the main database
// You have to be an admin to remove the design documents

object Wipe extends App {

  implicit val formats = DefaultFormats

  val config = Config("steenwerck.cfg")
  val hubCouch = new NioCouch(config.read[String]("master.host"),
			      config.read[Int]("master.port"),
			      Some(args(0), args(1)))
  val hubDatabase = Database(hubCouch, config.read[String]("master.dbname"))
  for ((id, _, value) <- hubDatabase.allDocs.execute.items[String, JObject])
    hubDatabase.delete(id, (value \ "rev").extract[String]).execute

  hubCouch.releaseExternalResources

}
