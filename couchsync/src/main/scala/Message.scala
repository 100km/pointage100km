import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._

object Message {

  def message(db: Database, msg: String) = {
    println("Displaying " + msg)
    db.update("bib_input", "local_status", "_local/status", Map("message" -> msg)).execute
    touchMe(db)
  }

  private def touchMe(db: Database) = {
    try {
      val touchMe = db("touch_me").execute
      db.insert(touchMe).execute
    } catch {
        case StatusCode(404, _) =>
          db.insert("touch_me", JObject(Nil)).execute
    }
  }

}
