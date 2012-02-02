import java.io.{File, FileWriter}
import java.lang.{Process, ProcessBuilder}
import Message._
import net.liftweb.json._
import net.rfc1149.canape._
import scala.io.Source
import scopt.OptionParser

object Replicator {

  implicit val formats = DefaultFormats

  def replicate(options: Options) {
    val c = new Replicator(options.localDir, options.usbDir)
    c.runCouchDb()
    Thread.sleep(5000)
    val couch = c.couch
    val db = couch.db("steenwerck100km")
    try {
      db.create.execute
    } catch {
      case StatusCode(412, _) => // Database already exists
    }
    val referenceDb = new NioCouch(options.hostName, auth = Some("admin", "admin")).db("steenwerck100km")
    message(referenceDb, "<blink>Ne pas enlever la clé USB</blink>")
    couch.replicate(db, referenceDb, false).execute
    couch.replicate(referenceDb, db, false).execute

    var activeTasksCount: Int = -2
    do {
      activeTasksCount = couch.activeTasks.execute.size
      println("Active tasks count: " + activeTasksCount)
      Thread.sleep(100)
    } while (activeTasksCount > 0)

    Thread.sleep(5000)

    c.stopCouchDb()

    message(referenceDb, "La clé USB peut être retirée")

    couch.releaseExternalResources
    referenceDb.couch.releaseExternalResources
  }
}

class Replicator(srcDir: File, usbBaseDir: File) {

  val dbDir = new File(usbBaseDir, "db")
  val etcDir = new File(usbBaseDir, "etc")
  val runDir = new File(usbBaseDir, "run")

  private[this] var process: Option[Process] = None

  val defaultFile = new File(etcDir, "default.ini")
  val logFile = new File(runDir, "couchdb.log")
  val pidFile = new File(runDir, "couchdb.pid")
  val uriFile = new File(runDir, "couchdb.uri")

  private[this] var _couch: Option[Couch] = None

  dbDir.mkdirs()
  etcDir.mkdirs()
  runDir.mkdirs()

  def fixDefaultIni() = {
    val ini = new Ini

    ini.load(Source.fromFile(new File(srcDir, "default.ini")))
    ini.load(Source.fromFile(new File(srcDir, "local.ini")))

    ini.set("couchdb", "database_dir", dbDir)
    ini.set("couchdb", "delayed_commits", false)
    ini.set("couchdb", "view_index_dir", dbDir)
    ini.set("couchdb", "uri_file", uriFile)
    ini.set("httpd", "bind_address", "127.0.0.1")
    ini.set("httpd", "port", 5983)
    ini.set("log", "file", logFile)
    ini.set("log", "level", "debug")

    val out = new FileWriter(defaultFile)
    ini.save(out)
    out.close()
  }

  def couch = _couch.get

  def runCouchDb() = {
    fixDefaultIni()

    val pb = new ProcessBuilder("couchdb",
				"-n", "-a", defaultFile.toString,
				"-p", pidFile.toString)
    pb.directory(usbBaseDir)
    process = Some(pb.start())

    _couch = Some(new NioCouch("localhost", 5983, Some("admin", "admin")))
  }

  def stopCouchDb() = {
    process foreach (_.destroy())
    process = None
    _couch = None
  }

}
