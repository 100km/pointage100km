import akka.actor.Props
import net.rfc1149.canape._
import play.api.libs.json.{JsValue, Json}
import steenwerck._

import scala.concurrent.Future
import scala.concurrent.duration._

object Replicate extends App {

  val options = Options.parse(args) getOrElse { sys.exit(1) }

  if (options.dryRun) {
    options.dump()
    sys.exit(0)
  }

  new Replicate(options)
}

class Replicate(options: Options.Config) {

  import implicits._

  private implicit val timeout: Duration = (5, SECONDS)

  import Global._

  private val localInfo = Json.obj("type" -> "site-info", "scope" -> "local", "site-id" -> options.siteId)

  private def createLocalInfo(db: Database) {
    val name = "site-info"
    try {
      db.insert(localInfo, name).execute()
    } catch {
      case Couch.StatusError(409, _, _) =>
        try {
          forceUpdate(db, name, localInfo).execute()
        } catch {
          case t: Exception =>
            log.warning("cannot force-update, hoping it is right: " + t)
        }
    }
  }

  private val localAuth = configurationFile.readOpt[String]("local.user").flatMap(user =>
    configurationFile.readOpt[String]("local.password").map(password => (user, password)))

  private val localCouch = new Couch(auth = localAuth)

  private val localDatabase =
    localCouch.db(configurationFile.readOpt[String]("local.dbname").getOrElse("steenwerck100km"))

  lazy private val hubCouch =
    if (options.replicate)
      Some(new Couch(configurationFile.read[String]("master.host"),
        configurationFile.read[Int]("master.port"),
        Some(configurationFile.read[String]("master.user"),
          configurationFile.read[String]("master.password"))))
    else
      None

  lazy private val cfgDatabase = hubCouch.map(_.db("steenwerck-config"))

  private lazy val remoteDbName: Option[String] = {
    var dbName: Option[String] = None
    if (options.replicate) {
      while (!dbName.isDefined) {
        try {
          dbName = cfgDatabase.map(db => (db("configuration").execute()(timeout) \ "dbname").as[String])
          dbName.foreach(log.info("server database name is {}", _))
        } catch {
          case t: Exception =>
            log.error("cannot retrieve database name: " + t)
            Thread.sleep(5000)
        }
      }
    }
    dbName
  }

  private lazy val previousDbName: Option[String] =
    try {
      Some((localDatabase("configuration").execute()(timeout) \ "dbname").as[String])
    } catch {
      case t: Exception =>
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
        case t: Exception =>
          log.error("deletion failed: " + t)
      }
    }
  }

  try {
    localDatabase.create().execute()
    log.info("database created")
  } catch {
    case Couch.StatusError(412, _, _) =>
      log.info("database already exists")
    case t: Exception =>
      log.error("cannot create database: " + t)
      localCouch.releaseExternalResources()
      exit(1)
  }

  try {
    if (!options.isSlave) {
      createLocalInfo(localDatabase)
      log.info("local information created")
    } else
      log.info("not creating local information on slave")
    if (options.replicate) {
      log.info("starting initial replication")
      try
        hubDatabase.foreach { hdb =>
          localDatabase.replicateFrom(hdb, Json.obj()).execute()
          log.info("initial replication done")
        }
      catch {
        case t: Exception =>
          log.error("initial replication failed: " + t)
      }
    }
  } catch {
    case t: Exception =>
      log.error("cannot create local information: " + t)
      exit(1)
  }

  if (options.initOnly) {
    localDatabase.ensureFullCommit()
    exit(0)
  } else {
    if (options.systematic)
      new Systematic(options, localDatabase, hubDatabase)
    if (options.obsolete)
      new LongShot(localDatabase)
    if (options.onChanges)
      system.actorOf(Props(new OnChanges(options, localDatabase)), "onChanges")
  }

  private def exit(status: Int) {
    localCouch.releaseExternalResources()
    system.shutdown()
    System.exit(status)
  }

}
