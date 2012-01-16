import dispatch.Http
import net.rfc1149.canape._

object Replicate {

  def startReplication(couch: Couch, local: Db, remote: Db, continuous: Boolean) = {
    try {
      Http(couch.replicate(local, remote, continuous))
      Http(couch.replicate(remote, local, continuous))
    } catch {
      case _ =>
    }
  }

  def main(args: Array[String]) = {
    val localCouch = Couch("admin", "admin")
    val localDb = Db(localCouch, "steenwerck100km")
    val hubCouch = Couch("tomobox.fr", 5984, "admin", "admin")
    val hubDb = Db(hubCouch, "steenwerck100km")
    while (true) {
      startReplication(localCouch, localDb, hubDb, true)
      Thread.sleep(5000)
    }
  }

}
