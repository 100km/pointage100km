import akka.actor.ActorSystem
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import scopt.OptionParser

object Wipe extends App {

  import implicits._

  val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  private object Options {
    var login: String = _
    var password: String = _
  }

  private val parser = new OptionParser("wipe") {
    help("h", "help", "show this help")
    arg("login", "login to access the master database", { s: String => Options.login = s })
    arg("password", "pasword to access the master database", { s: String => Options.password = s })
  }

  if (!parser.parse(args))
    sys.exit(1)

  val config = Config("steenwerck.cfg", "../steenwerck.cfg", "../../steenwerck.cfg")
  val hubCouch = new NioCouch(config.read[String]("master.host"),
			      config.read[Int]("master.port"),
			      Some(Options.login, Options.password))

  val cfgDatabase = hubCouch.db("steenwerck-config")

  val newName = try {
    val oldNameDoc = cfgDatabase("configuration").execute()
    val oldName = oldNameDoc("dbname").extract[String]
    val newCount = oldName.substring(11).toInt
    val newName = "steenwerck-" + (newCount + 1)
    cfgDatabase.insert(oldNameDoc + ("dbname" -> newName)).execute()
    newName
  } catch {
    case t =>
      try {
	cfgDatabase.create()
      } catch {
	case e =>
	  println("Cannot create configuration database: " + e)
      }
      cfgDatabase.insert(Map("dbname" -> "steenwerck-0"), "configuration").execute()
      "steenwerck-0"
  }

  val hubDatabase = hubCouch.db(newName)
  try {
    println("Creating database " + newName)
    hubDatabase.create().execute()
    println("Copying security document")
    hubDatabase.insert(cfgDatabase("_security").execute(), "_security").execute()
    println("Inserting configuration document")
    hubDatabase.insert(Map("dbname" -> newName), "configuration").execute()
    println("Generating random identification for couchsync")
    val key = new Array[Byte](256)
    scala.util.Random.nextBytes(key)
    val md = java.security.MessageDigest.getInstance("SHA-1")
    val ha = new sun.misc.BASE64Encoder().encode(md.digest(key))
    hubDatabase.insert(Map("key" -> ha), "couchsync").execute()
    println("All things done")
  } catch {
      case StatusCode(401, _) =>
	println("You are not authorized to perform this operation")
      case t =>
	println("Exception caught: " + t)
  }

  hubCouch.releaseExternalResources()
  system.shutdown()

}
