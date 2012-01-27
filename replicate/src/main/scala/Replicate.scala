import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import net.rfc1149.canape.helpers._
import scopt.OptionParser

object Replicate extends App {

  private object Options {
    var initOnly: Boolean = false
    var siteId: Int = _
  }

  private val parser = new OptionParser("replicate") {
    help("h", "help", "show this help")
    opt("i", "init-only", "initialize database but do not start tasks", { Options.initOnly = true })
    arg("site_id", "numerical id of the current site", { s: String => Options.siteId = Integer.parseInt(s) })
  }

  if (!parser.parse(args))
    sys.exit(1)

  import Global._

  private def createLocalInfo(db: Database, site: Int) = {
    val name = "_local/site-info"
    val doc: mapObject = try {
      db(name).execute
    } catch {
	case StatusCode(404, _) => Map()
    }
    val newDoc = doc + ("site-id" -> JInt(site))
    db.insert(name, newDoc).execute
    touchMe(db)
  }

  private def touchMe(db: Database) = {
    try {
      val touchMe = db("touch_me").execute
      db.insert(touchMe).execute
    } catch {
	case StatusCode(404, _) =>
	  db.insert("touch_me", JObject(Nil)).execute
    }
  }

  private val localCouch = new NioCouch(auth = Some("admin", "admin"))
  private val localDatabase = localCouch.db("steenwerck100km")

  try {
    localDatabase.create.execute
  } catch {
    case StatusCode(412, _) =>
      log.info("database already exists")
    case t =>
      log.error("cannot create database: " + t)
      localCouch.releaseExternalResources
      exit(1)
  }

  try {
    createLocalInfo(localDatabase, Options.siteId)
  } catch {
    case t =>
      log.error("cannot create local information: " + t)
      localCouch.releaseExternalResources
      exit(1)
  }

  if (Options.initOnly)
    exit(0)
  else {
    {
      val hubCouch = new NioCouch(config.read[String]("master.host"),
				  config.read[Int]("master.port"),
				  Some(config.read[String]("master.user"),
				       config.read[String]("master.password")))
      val hubDatabase = hubCouch.db(config.read[String]("master.dbname"))
      createActor(new ReplicationActor(localCouch, localDatabase, hubDatabase), "replication")
    }
    createActor(new ConflictsSolverActor(localDatabase), "conflictsSolver")
    createActor(new IncompleteCheckpointsActor(localDatabase), "incompleteCheckpoints")
  }

  private def exit(status: Int) = {
    localCouch.releaseExternalResources
    system.shutdown
    System.exit(status)
  }
}
