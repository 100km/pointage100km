import akka.actor.{DeadLetterActorRef, Props}
import akka.dispatch.Future
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import scopt.OptionParser
import steenwerck._

object Replicate extends App {

  private object Options {
    var initOnly: Boolean = false
    var siteId: Int = _
  }

  private val parser = new OptionParser("replicate") {
    help("h", "help", "show this help")
    opt("i", "init-only", "initialize database but do not start tasks", {
      Options.initOnly = true
    })
    arg("site_id", "numerical id of the current site", {
      s: String => Options.siteId = Integer.parseInt(s)
    })
  }

  if (!parser.parse(args))
    sys.exit(1)

  import Global._

  private val localInfo = ("type" -> "site-info") ~ ("site-id" -> Options.siteId)

  private def createLocalInfo(db: Database, site: Int) {
    val name = "_local/site-info"
    try {
      db.insert(localInfo, name).thenTouch(db).execute()
    } catch {
      case StatusCode(409, _) =>
	forceUpdate(db, name, localInfo).thenTouch(db).execute()
    }
  }

  def ping(db: Database): Future[JValue] = steenwerck.pingResolve(db, Options.siteId)

  def siteId = Options.siteId

  private val localCouch = new NioCouch(auth = Some("admin", "admin"))
  private val localDatabase = localCouch.db("steenwerck100km")

  try {
    localDatabase.create().execute()
  } catch {
    case StatusCode(412, _) =>
      log.info("database already exists")
    case t =>
      log.error("cannot create database: " + t)
      localCouch.releaseExternalResources()
      exit(1)
  }

  try {
    createLocalInfo(localDatabase, Options.siteId)
  } catch {
    case t =>
      log.error("cannot create local information: " + t)
      exit(1)
  }

  if (Options.initOnly)
    exit(0)
  else {
    val hubCouch = new NioCouch(config.read[String]("master.host"),
      config.read[Int]("master.port"),
      Some(config.read[String]("master.user"),
        config.read[String]("master.password")))
    val hubDatabase = hubCouch.db(config.read[String]("master.dbname"))
    system.actorOf(Props(new Systematic(localDatabase, hubDatabase)), "systematic")
    system.actorOf(Props(new OnChanges(localDatabase)), "onChanges")
  }

  private def exit(status: Int) {
    localCouch.releaseExternalResources()
    system.shutdown()
    System.exit(status)
  }

}
