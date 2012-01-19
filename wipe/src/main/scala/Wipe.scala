import dispatch._
import net.liftweb.json._
import net.rfc1149.canape._

// Usage: wipe login password
// Wipe everything on the main database
// You have to be an admin to remove the design documents

object Wipe {

  implicit val formats = DefaultFormats

  def main(args: Array[String]) = {
    val config = Config("steenwerck.cfg")
    val hubCouch = Couch(config.read[String]("master.host"),
			 config.read[Int]("master.port"),
			 args(0),
			 args(1))
    val hubDatabase = Database(hubCouch, config.read[String]("master.dbname"))
    for (doc <- Http(hubDatabase.allDocs()).rows) {
      Http(hubDatabase.delete(doc.id, doc.value("rev").extract[String]))
    }
  }

}
