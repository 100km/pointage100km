import akka.actor.ActorSystem
import net.liftweb.json._
import net.rfc1149.canape._
import scopt.OptionParser

object Wipe extends App {

  implicit val formats = DefaultFormats

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

  val config = Config("steenwerck.cfg")
  val hubCouch = new NioCouch(config.read[String]("master.host"),
			      config.read[Int]("master.port"),
			      Some(Options.login, Options.password))
  val hubDatabase = Database(hubCouch, config.read[String]("master.dbname"))
  try {
    for ((id, _, value) <- hubDatabase.allDocs().execute().items[String, JObject])
      hubDatabase.delete(id, (value \ "rev").extract[String]).execute()
  } catch {
      case StatusCode(401, _) =>
	println("You are not authorized to perform this operation")
      case t =>
	println("Exception caught: " + t)
  }

  hubCouch.releaseExternalResources()
  system.shutdown()

}
