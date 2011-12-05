import dispatch.Http
import net.rfc1149.canape._

object Replicate {

  def startReplication(couch: Couch, local: Db, remote: Db, continuous: Boolean) = {
    Http(couch.replicate(local, remote, continuous))
    Http(couch.replicate(remote, local, continuous))
  }

  def main(args: Array[String]) = {
    val localCouch = Couch("sam", "toto")
    val localDb = Db(localCouch, "steenwerck100km")
    val hubCouch = Couch("couchdb.mindslicer.com", 80, "admin", "p4p4n03l")
    val hubDb = Db(hubCouch, "steenwerck100km")
    startReplication(localCouch, localDb, hubDb, true)
  }

}
