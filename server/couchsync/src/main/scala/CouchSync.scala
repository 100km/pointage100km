import java.io.File
import scopt.OptionParser

object CouchSync extends App {

  val options = new Options

  private val parser = new OptionParser("couchsync") {
    opt("c", "continuous", "allow continuous watch and replication", options.continuous = true)
    help("h", "help", "show this help")
    arg("local", "local database directory", { s: String => options.localDir = new File(s) })
    arg("usb_key", "usb key directory", { s: String => options.usbDir = new File(s) })
    arg("host", "local host name", { options.hostName = _ })
  }

  if (!parser.parse(args))
    sys.exit(1)

  if (options.continuous) {
    val checker = new DirChecker(options.usbDir, 1000)
    while (true) {
      val candidate = checker.suitable
      Replicator.replicate(options, Some(candidate))
    }
  } else {
    Replicator.replicate(options, None)
    Replicator.shutdown()
  }

}
