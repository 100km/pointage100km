package replicate

import akka.actor.Props
import akka.event.Logging
import net.rfc1149.canape._
import play.api.libs.json.Json
import replicate.alerts.Alerts
import replicate.maintenance.RemoveObsoleteDocuments
import replicate.messaging.sms.TextService
import replicate.scrutineer.CheckpointScrutineer
import replicate.stalking.Stalker
import replicate.utils.Options.Checkpoint
import replicate.utils._
import steenwerck._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Replicate extends App {

  val options = Options.parse(args) getOrElse { sys.exit(1) }

  if (options.dryRun) {
    options.dump()
    sys.exit(0)
  }

  new Replicate(options)
}

class Replicate(options: Options.Config) extends LoggingError {

  import Global._
  import implicits._

  override val log = Logging(Global.system, "Replicate")

  private implicit val timeout: Duration = (5, SECONDS)

  private val localInfo = Json.obj("type" → "site-info", "scope" → "local", "site-id" → options.siteId)

  private def createLocalInfo(db: Database) {
    val name = "site-info"
    try {
      if (options.resetSiteId)
        db.insert(localInfo, name).execute()
    } catch {
      case Couch.StatusError(409, _, _) ⇒
        try {
          forceUpdate(db, name, localInfo).execute()
        } catch {
          case t: Exception ⇒
            log.error(t, "cannot force-update, hoping it is right")
        }
    }
  }

  private val localCouch = steenwerck.localCouch

  val localDatabase = localCouch.db(steenwerck.localDbName)

  lazy private val hubCouch = steenwerck.masterCouch()

  lazy private val cfgDatabase = hubCouch.db("steenwerck-config")

  private lazy val remoteDbName: String = {
    var dbName: Option[String] = None
    if (options.replicate) {
      while (dbName.isEmpty) {
        try {
          Global.configuration = Some(cfgDatabase("configuration").execute()(timeout).as[Configuration])
          dbName = Global.configuration.map(_.dbname)
          dbName.foreach(log.info("server database name is {}", _))
        } catch {
          case t: Throwable ⇒
            log.error(t, "cannot retrieve database name")
            Thread.sleep(5000)
        }
      }
    }
    dbName.get
  }

  private lazy val previousDbName: Option[String] =
    try {
      Some(localDatabase("configuration").execute()(timeout).as[Configuration].dbname)
    } catch {
      case t: Exception ⇒
        log.info("cannot retrieve previous database name: {}", t.getMessage)
        None
    }

  private lazy val hubDatabase = hubCouch.db(remoteDbName)

  if (options.replicate) {
    if (!previousDbName.contains(remoteDbName)) {
      log.info("deleting previous database")
      try {
        localDatabase.delete().execute()
      } catch {
        case t: Exception ⇒
          log.error(t, "deletion failed")
      }
    }
  }

  try {
    localDatabase.create().execute()
    log.info("database created")
  } catch {
    case Couch.StatusError(412, _, _) ⇒
      log.info("database already exists")
    case t: Exception ⇒
      log.error(t, "cannot create database")
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
      try {
        localDatabase.replicateFrom(hubDatabase).execute()(initialReplicationTimeout)
        log.info("initial replication done")
        val loadInfos = localDatabase("infos") map (_.as[Infos]) andThen {
          case Success(i) ⇒
            Global.infos = Some(i)
            log.info("race information loaded from database")
          case Failure(t) ⇒
            log.error(t, "unable to read race information from database")
        }
        loadInfos.execute()
      } catch {
        case t: Exception ⇒
          log.error(t, "initial replication failed")
      }
    }
  } catch {
    case t: Exception ⇒
      log.error(t, "cannot create local information")
      exit(1)
  }

  if (options.initOnly) {
    localDatabase.ensureFullCommit()
    exit(0)
  } else {
    if (options.replicate) {
      val (downloadFilter, uploadFilter) =
        if (options.mode == Checkpoint) {
          val sites = Global.infos.get.sites.length
          val queryParams = Json.obj(
            "site_id" → options.siteId.toString,
            "prev_site_id" → ((options.siteId + sites - 1) % sites).toString
          )
          (Json.obj("filter" -> "replicate/to-download", "query_params" -> queryParams), Json.obj("filter" -> "replicate/to-upload"))
        } else
          (Json.obj("filter" -> "replicate/no-local"), Json.obj("filter" -> "replicate/no-local"))
      val replicateDownloadOptions = downloadFilter ++ Json.obj("continuous" → true)
      val replicateUploadOptions = uploadFilter ++ Json.obj("continuous" → true)
      system.scheduler.schedule(0.seconds, replicateRelaunchInterval) {
        withError(localDatabase.replicateFrom(hubDatabase, replicateDownloadOptions), "cannot start remote to local replication")
        if (!options.isSlave) {
          withError(localDatabase.replicateTo(hubDatabase, replicateUploadOptions), "cannot start local to remote replication")
        }
      }
    }
    if (options.compactLocal)
      system.scheduler.schedule(localCompactionInterval, localCompactionInterval) {
        withError(localDatabase.compact(), "cannot start local database compaction")
      }
    if (options.compactMaster)
      system.scheduler.schedule(localCompactionInterval, localCompactionInterval) {
        withError(hubDatabase.compact(), "cannot start master database compaction")
      }
    if (options.obsolete)
      system.scheduler.schedule(obsoleteRemoveInterval, obsoleteRemoveInterval) {
        withError(
          RemoveObsoleteDocuments.removeObsoleteDocuments(localDatabase, log),
          "cannot remove obsolete documents"
        )
      }
    if (options.onChanges)
      system.actorOf(Props(new OnChanges(options, localDatabase)), "onChanges")
    if (options.alerts) {
      CheckpointScrutineer.startCheckpointScrutineer(localDatabase)(log, Global.flowMaterializer)
      system.actorOf(Props(new Alerts(localDatabase)), "alerts")
    }
    if (options.stalking) {
      val textService = system.actorOf(Props(new TextService), "textService")
      system.actorOf(Props(new Stalker(localDatabase, textService)), "stalker")
    }
  }

  private def exit(status: Int) {
    localCouch.releaseExternalResources().execute()
    hubCouch.releaseExternalResources().execute()
    system.terminate()
    System.exit(status)
  }

}
