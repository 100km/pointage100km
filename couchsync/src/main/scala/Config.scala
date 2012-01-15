import dispatch._
import java.io.{File, FileWriter}
import java.lang.{Process, ProcessBuilder}
import net.liftweb.json._
import net.rfc1149.canape._
import scala.io.Source

object Config {

  def main(args: Array[String]): Unit = {
    val c = new Config(new File(args(0)), new File(args(1)))
    c.runCouchDb()
    Thread.sleep(5000)
    val couch = c.couch
    val db = couch.db("steenwerck100km")
    try {
      Http(db.create())
    } catch {
	case _ =>
    }
    val referenceDb = Couch(args(2), "admin", "admin").db("steenwerck100km")
    Http(couch.replicate(db, referenceDb, false))
    Http(couch.replicate(referenceDb, db, false))

    var activeTasksCount: Int = -2
    do {
      activeTasksCount = Http(couch.activeTasks).children.size
      println("Active tasks count: " + activeTasksCount)
      Thread.sleep(100)
    } while (activeTasksCount > 0)

    db.startCompaction
    while (Http(db.status).compact_running) {
      println("Compaction running")
      Thread.sleep(100)
    }

    c.stopCouchDb()
  }

}

class Config(srcDir: File, usbBaseDir: File) {

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
    ini.set("couchdb", "index_dir", dbDir)
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

    val pb = new ProcessBuilder("/usr/bin/couchdb",
				"-n", "-a", defaultFile.toString,
				"-p", pidFile.toString)
    pb.directory(usbBaseDir)
    process = Some(pb.start())

    _couch = Some(Couch("localhost", 5983, "admin", "admin"))
  }

  def stopCouchDb() = {
    process foreach (_.destroy())
    process = None
    _couch = None
  }

}
