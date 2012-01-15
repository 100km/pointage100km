import java.io.{File, FileWriter}
import java.lang.{Process, ProcessBuilder}
import scala.io.Source

object Config {

  def main(args: Array[String]) = {
    val c = new Config(new File(args(0)), new File(args(1)))
    c.fixDefaultIni()
    c.runCouchDb()
  }

}

class Config(srcDir: File, usbBaseDir: File) {

  val dbDir = new File(usbBaseDir, "db")
  val etcDir = new File(usbBaseDir, "etc")
  val runDir = new File(usbBaseDir, "run")

  dbDir.mkdirs()
  etcDir.mkdirs()
  runDir.mkdirs()

  val defaultFile = new File(etcDir, "default.ini")
  val localFile = new File(srcDir, "local.ini")
  val logFile = new File(runDir, "couchdb.log")
  val pidFile = new File(runDir, "couchdb.pid")
  val uriFile = new File(runDir, "couchdb.uri")

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

  def runCouchDb(): Process = {
    val pb = new ProcessBuilder("/usr/bin/couchdb",
				"-n", "-a", defaultFile.toString,
				"-p", pidFile.toString)
    pb.directory(usbBaseDir)
    pb.start()
  }
}
