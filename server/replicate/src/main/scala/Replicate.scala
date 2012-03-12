import akka.actor.{DeadLetterActorRef, Props}
import akka.dispatch.Future
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import scopt.OptionParser
import steenwerck._

object Replicate extends App {

  trait Options {
    def compact: Boolean
    def fixConflicts: Boolean
    def fixIncomplete: Boolean
    def obsolete: Boolean
    def replicate: Boolean
    def siteId: Int
    def watchdog: Boolean

    def onChanges = fixConflicts || fixIncomplete || watchdog
    def systematic = compact || replicate
    def initOnly = !onChanges && !systematic
  }

  private object _options extends Options {
    var compact: Boolean = true
    var fixConflicts: Boolean = false
    var fixIncomplete: Boolean = false
    var obsolete: Boolean = true
    var replicate: Boolean = false
    var watchdog: Boolean = true
    var siteId: Int = _
  }

  val options: Options = _options

  private val parser = new OptionParser("replicate") {
    opt("c", "conflicts", "fix conflicts as they appear", { _options.fixConflicts = true })
    opt("f", "full", "turn on every service", {
      _options.compact = true
      _options.fixConflicts = true
      _options.fixIncomplete = true
      _options.obsolete = true
      _options.replicate = true
      _options.watchdog = true
    })
    help("h", "help", "show this help")
    opt("i", "init-only", "turn off every service", {
      _options.compact = false
      _options.fixConflicts = false
      _options.fixIncomplete = false
      _options.obsolete = false
      _options.replicate = false
      _options.watchdog = false
    })
    opt("I", "incomplete", "fix incomplete checkpoints", { _options.fixIncomplete = true })
    opt("nc", "no-compact", "compact database regularly", { _options.compact = false })
    opt("no", "no-obsolete", "do not remove obsolete documents", { _options.obsolete = false })
    opt("nw", "no-watchdog", "do not start the watchdog", { _options.watchdog = false })
    opt("r", "replicate", "start replication", { _options.replicate = true })
    arg("site_id", "numerical id of the current site", {
      s: String => _options.siteId = Integer.parseInt(s)
    })
  }

  if (!parser.parse(args))
    sys.exit(1)

  import Global._

  private val localInfo = ("type" -> "site-info") ~ ("site-id" -> options.siteId)

  private def createLocalInfo(db: Database) {
    val name = "_local/site-info"
    try {
      db.insert(localInfo, name).execute()
    } catch {
      case StatusCode(409, _) =>
	forceUpdate(db, name, localInfo).execute()
    }
    // Force change to be visible immediately and synchronously in case we are exiting.
    // If we cannot perform a touch, it means that we dont' have the update function yet,
    // the replication will take care of that.
    try {
      touch(db).execute()
    } catch {
	case StatusCode(404, _) =>
    }
  }

  def ping(db: Database): Future[JValue] = steenwerck.ping(db, options.siteId).toFuture

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
    createLocalInfo(localDatabase)
  } catch {
    case t =>
      log.error("cannot create local information: " + t)
      exit(1)
  }

  if (options.initOnly)
    exit(0)
  else {
    val hubCouch = new NioCouch(config.read[String]("master.host"),
      config.read[Int]("master.port"),
      Some(config.read[String]("master.user"),
        config.read[String]("master.password")))
    val hubDatabase = hubCouch.db(config.read[String]("master.dbname"))
    if (options.systematic)
      new Systematic(localDatabase, hubDatabase)
    if (options.obsolete)
      new LongShot(localDatabase)
    if (options.onChanges)
      system.actorOf(Props(new OnChanges(localDatabase)), "onChanges")
  }

  private def exit(status: Int) {
    localCouch.releaseExternalResources()
    system.shutdown()
    System.exit(status)
  }

}
