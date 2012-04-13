import java.io.File
import scopt.OptionParser

object CouchSync extends App {

  val options = new Options

  private val parser = new OptionParser("couchsync") {
    help("h", "help", "show this help")
    arg("local", "local database directory", { s: String => options.localDir = new File(s) })
    arg("usb_key", "usb key directory", { s: String => options.usbDir = new File(s) })
    arg("host", "local host name", { options.hostName = _ })
  }

  if (!parser.parse(args))
    sys.exit(1)

  Replicator.replicate(options)
}
