package replicate

import akka.actor.Props
import akka.event.Logging
import akka.stream.scaladsl.{Broadcast, Flow, Sink}
import net.rfc1149.canape.Couch.StatusError
import net.rfc1149.canape._
import play.api.libs.json.{JsObject, Json}
import replicate.alerts.{Alerts, RankingAlert}
import replicate.maintenance.RemoveObsoleteDocuments
import replicate.messaging.sms.TextService
import replicate.scrutineer.Analyzer.ContestantAnalysis
import replicate.scrutineer.{AnalysisService, CheckpointScrutineer}
import replicate.stalking.StalkingService
import replicate.state.{ContestantState, RankingState}
import replicate.utils.Options.{Checkpoint, Master}
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

  /**
   * Asynchronously create system databases needed in CouchDB 2.0.0 if they don't exist .This
   * is not an error if we cannot create them since CouchDB works fine without them.
   */
  private def createSystemDatabases() = {
    for (systemDb ← Seq("_global_changes", "_metadata", "_replicator", "_users"))
      localCouch.db(systemDb).create().onComplete {
        case Success(_)                      ⇒ log.info("created database {}", systemDb)
        case Failure(StatusError(412, _, _)) ⇒ // Nothing to do
        case Failure(f)                      ⇒ log.info("could not create database {}: {}", systemDb, f)
      }
  }

  private val localCouch = steenwerck.localCouch(Global.replicateConfig)

  val localDatabase = localCouch.db(steenwerck.localDbName)

  lazy private val hubCouch = steenwerck.masterCouch(Global.replicateConfig)

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

  createSystemDatabases()

  if (options.replicate) {
    if (previousDbName.contains(remoteDbName))
      log.info("reusing existing local database")
    else {
      try {
        localDatabase.delete().execute()
        log.info("previous local database deleted")
      } catch {
        case StatusError(404, _, _) ⇒
          log.info("previous local database did not exist, nothing to delete")
        case t: Exception ⇒
          log.error(t, "deletion of previous local database failed, cannot continue")
          exit(1)
      }
      try {
        localDatabase.create().execute()
        log.info("local database created")
      } catch {
        case t: Exception ⇒
          log.error(t, "cannot create local database")
          exit(1)
      }
    }
  }

  val proxyOptions: JsObject = steenwerck.proxyUrl.fold(Json.obj())(url ⇒ Json.obj("proxy" → url))

  try {
    if (!options.isSlave) {
      createLocalInfo(localDatabase)
      log.info("local information created")
    } else
      log.info("not creating local information on slave")
    if (options.replicate) {
      log.info("starting initial replication")
      val replicationDeadline = initialReplicationTimeout.fromNow
      var loaded = false
      while (!loaded)
        try {
          localDatabase.replicateFrom(hubDatabase, proxyOptions).execute()(initialReplicationTimeout)
          log.info("initial replication done")
          val loadInfos = localDatabase("infos") map (_.as[Infos]) andThen {
            case Success(i) ⇒
              Global.infos = Some(i)
              log.info("race information loaded from database")
            case Failure(t) ⇒
              log.error(t, "unable to read race information from database")
              exit(1)
          }
          loadInfos.execute()
          loaded = true
        } catch {
          case t: Exception ⇒
            // Sometimes, since nothing passes on the TCP stream, the connection aborts while the
            // replication is still in progress. We can just restart it as it will be an idempotent
            // operation.
            if (replicationDeadline.hasTimeLeft()) {
              log.info("initial replication failed, retrying")
            } else {
              log.error(t, "initial replication failed")
              exit(1)
            }
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
            "prev_site_id" → ((options.siteId + sites - 1) % sites).toString)
          (Json.obj("filter" → "replicate/to-download", "query_params" → queryParams), Json.obj("filter" → "replicate/to-upload"))
        } else
          (Json.obj("filter" → "replicate/no-local"), Json.obj("filter" → "replicate/no-local"))
      val replicateDownloadOptions = downloadFilter ++ Json.obj("continuous" → true) ++ proxyOptions
      val replicateUploadOptions = uploadFilter ++ Json.obj("continuous" → true) ++ proxyOptions
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
          "cannot remove obsolete documents")
      }

    if (options.onChanges)
      system.actorOf(Props(new OnChanges(options, localDatabase)), "onChanges")

    if (options.alerts)
      system.actorOf(Props(new Alerts(localDatabase)), "alerts")

    if (options.mode == Master) {
      // Load the contestant information
      ContestantState.startContestantAgent(localDatabase)(log, flowMaterializer)

      // Start to analyze the checkpoints, with a marker telling if we are in the initial state
      val checkpointScrutineerSource = CheckpointScrutineer.checkpointScrutineer(localDatabase)(log, flowMaterializer)

      // If we have activated the stalking service, build a sink for it, or ignore
      val stalkingSink = if (options.stalking) {
        val textService = system.actorOf(Props(new TextService), "text-service")
        // Include initial data as we may have missed some text messages to send while we were out.
        Flow[(ContestantAnalysis, Boolean)].map(_._1)
          .via(Global.TextMessages.ifAnalysisUnchanged)
          .to(StalkingService.stalkingServiceSink(localDatabase, textService))
      } else
        Sink.ignore

      // Write all documents to the database
      val analysisServiceSink = Flow[(ContestantAnalysis, Boolean)].map(_._1).to(AnalysisService.analysisServiceSink(localDatabase))

      // Enter rankings all the times, but do not send alerts until we are no longer in the initial mode
      val rankingAlertSink = Flow[(ContestantAnalysis, Boolean)].mapAsync(1) {
        case (analysis, initial) ⇒
          RankingState.enterContestant(analysis).map((_, initial))
      }.collect { case (rankingInfo, initial) if !initial ⇒ rankingInfo }.to(RankingAlert.rankingAlertSink)

      checkpointScrutineerSource.runWith(Sink.combine(stalkingSink, analysisServiceSink, rankingAlertSink)(Broadcast(_)))
    }

  }

  private def exit(status: Int) {
    localCouch.releaseExternalResources().execute()
    hubCouch.releaseExternalResources().execute()
    system.terminate()
    System.exit(status)
  }

}
