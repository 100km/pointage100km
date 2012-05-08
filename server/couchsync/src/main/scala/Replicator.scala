import akka.actor.ActorSystem
import akka.dispatch.{Await, Future}
import akka.util.duration._
import java.io.File
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import steenwerck._

object Replicator {

  implicit val formats = DefaultFormats

  val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  private def waitForNoTasks(couch: Couch) = {
    var activeTasksCount: Int = 0
    do {
      activeTasksCount = couch.activeTasks().execute().size
      Thread.sleep(100)
    } while (activeTasksCount > 0)
  }

  def replicate(options: Options, dir: Option[File]) {
    val referenceDb = new NioCouch(options.hostName, auth = Some("admin", "admin")).db("steenwerck100km")

    val siteId = referenceDb("site-info").execute()("site-id").extract[Int]

    def step(msg: String, warning: Boolean = true) =
      message(referenceDb, (if (warning) "Ne pas enlever la clé USB - " else "") + msg).toFuture

    def wait(msg: String, seconds: Int)(interruptIf: => Boolean) =
      for (i <- 1 to seconds) {
        if (!interruptIf) {
          step(msg + " (" + i + "/" + seconds + ")")
          Thread.sleep(1000)
        }
      }

    step("construction de la configuration")

    val c = new SlaveDb(options.localDir, dir.getOrElse(options.usbDir))
    c.runCouchDb()
    val couch = c.couch
    wait("lancement de la base sur clé USB", 30) {
      try {
        Await.result(couch.status().toFuture map { _ => true } recover { case _ => false }, 100 milliseconds)
      } catch {
        case _ => false
      }
    }
    val db = couch.db("steenwerck100km")
    try {
      db.create().execute()
    } catch {
      case StatusCode(412, _) => // Database already exists
      case e: java.net.ConnectException =>
        step("impossible de démarrer la base sur clé USB", false)
        throw e
    }
    step("synchronisation")
    referenceDb.replicateTo(db, ("filter" -> "common/to-replicate")).execute()
    referenceDb.replicateFrom(db, ("filter" -> "common/to-replicate")).execute()

    step("compaction")
    db.compact().execute()
    referenceDb.compact().execute()
    waitForNoTasks(couch)

    step("écriture")
    db.ensureFullCommit().execute()

    step("vidage du cache")
    (new ProcessBuilder("sync")).start()

    wait("écriture finale sur clé", 5) { false }

    c.stopCouchDb()

    val end = step("La clé USB peut être retirée", false)
    Await.ready(end, 5 seconds)

    Thread.sleep(5000)
    referenceDb.delete(referenceDb("status").execute()).execute()

    couch.releaseExternalResources()
    referenceDb.couch.releaseExternalResources()
  }

  def shutdown() {
    system.shutdown()
  }
}

