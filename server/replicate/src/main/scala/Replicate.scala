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

  lazy private val hubCouch =
    if (options.replicate)
      Some(new NioCouch(config.read[String]("master.host"),
			config.read[Int]("master.port"),
			Some(config.read[String]("master.user"),
			     config.read[String]("master.password"))))
    else
      None

  lazy private val cfgDatabase = hubCouch.map(_.db("steenwerck-config"))

  private lazy val remoteDbName: Option[String] = {
    var dbName: Option[String] = None
    if (options.replicate) {
      while (!dbName.isDefined) {
	try {
	  dbName = cfgDatabase.map(_("configuration").execute()("dbname").extract[String])
	  dbName.foreach(log.info("server database name is {}", _))
	} catch {
	  case t =>
	    log.error("cannot retrieve database name: " + t)
	    Thread.sleep(5000)
	}
      }
    }
    dbName
  }

  private lazy val previousDbName: Option[String] =
    try {
      Some(localDatabase("configuration").execute()("dbname").extract[String])
    } catch {
      case t =>
	log.info("cannot retrieve previous database name: " + t)
	None
    }

  private lazy val hubDatabase = for (c <- hubCouch; name <- remoteDbName) yield c.db(name)

  if (options.replicate) {
    if (previousDbName != remoteDbName) {
      log.info("deleting previous database")
      try {
	localDatabase.delete().execute()
      } catch {
	case t =>
	  log.error("deletion failed: " + t)
      }
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

  try {
    createLocalInfo(localDatabase)
    log.info("local information created")
    if (options.replicate) {
      log.info("starting initial replication")
      try
	hubDatabase.foreach { hdb =>
	  localDatabase.replicateFrom(hdb, Map[String, String]()).execute()
	  log.info("initial replication done")
	}
      catch {
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
