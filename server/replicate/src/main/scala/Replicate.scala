import akka.actor.{DeadLetterActorRef, Props}
import akka.dispatch.Future
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import steenwerck._

object Replicate extends App {

  private implicit val formats = DefaultFormats

  val options = new Options("replicate")

  if (!options.parse(args))
    sys.exit(1)

  import Global._

  private val localInfo = ("type" -> "site-info") ~
			  ("scope" -> "local") ~
			  ("site-id" -> options.siteId)

  private def createLocalInfo(db: Database) {
    val name = "site-info"
    try {
      db.insert(localInfo, name).execute()
    } catch {
      case StatusCode(409, _) =>
	try {
	  forceUpdate(db, name, localInfo).execute()
	} catch {
	  case t =>
	    log.warning("cannot force-update, hoping it is right: " + t)
	}
    }
  }

  def ping(db: Database): Future[JValue] = steenwerck.ping(db, options.siteId).toFuture

  private val localCouch = new NioCouch(auth = Some("admin", "admin"))
  private val localDatabase = localCouch.db("steenwerck100km")
  private val hubCouch = new NioCouch(config.read[String]("master.host"),
				      config.read[Int]("master.port"),
				      Some(config.read[String]("master.user"),
					   config.read[String]("master.password")))
  private val cfgDatabase = hubCouch.db("steenwerck-config")

  var dbName: Option[String] = None
  while (!dbName.isDefined) {
    try {
      dbName = Some(cfgDatabase("configuration").execute()("dbname").extract[String])
      log.info("server database name is " + dbName.get)
    } catch {
	case t =>
	  log.error("cannot retrieve database name: " + t)
	Thread.sleep(5000)
    }
  }

  var previousDbName = try {
    Some(localDatabase("configuration").execute()("dbname").extract[String])
  } catch {
      case t =>
	log.info("cannot retrieve previous database name: " + t)
        None
  }

  if (previousDbName != dbName) {
    log.info("deleting previous database")
    try {
      localDatabase.delete().execute()
    } catch {
      case t =>
	log.error("deletion failed: " + t)
    }
  }

  try {
    localDatabase.create().execute()
    log.info("database created")
  } catch {
    case StatusCode(412, _) =>
      log.info("database already exists")
    case t =>
      log.error("cannot create database: " + t)
      localCouch.releaseExternalResources()
      exit(1)
  }

  val hubDatabase = hubCouch.db(dbName.get)

  try {
    createLocalInfo(localDatabase)
    log.info("local information created")
    if (options.replicate) {
      log.info("starting initial replication")
      try {
	localDatabase.replicateFrom(hubDatabase, Map[String, String]()).execute()
	log.info("initial replication done")
      } catch {
	case t =>
	  log.error("initial replication failed: " + t)
      }
    }
  } catch {
    case t =>
      log.error("cannot create local information: " + t)
      exit(1)
  }

  if (options.initOnly)
    exit(0)
  else {
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
