package replicate.alerts

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.{Done, NotUsed}
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRefFactory, Cancellable, Props}
import akka.stream._
import akka.stream.scaladsl._
import net.rfc1149.canape.Database
import play.api.libs.json.{JsObject, Json}
import replicate.messaging.Message
import replicate.messaging.Message.{Checkpoint, Severity}
import replicate.state.PingState
import replicate.utils.Global.CheckpointAlerts._
import replicate.utils.Types.SiteId
import replicate.utils._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scalaz.@@

object PingAlert {

  private[this] lazy val checkpoints = Global.infos.get.checkpoints

  private[this] implicit val dispatcher = Global.system.dispatcher

  sealed trait State
  case object Starting extends State
  case object Inactive extends State
  case object Up extends State
  case object Notice extends State
  case object Warning extends State
  case object Critical extends State

  private class CheckpointWatcher(siteId: Int @@ SiteId, database: Database) extends Actor with ActorLogging {

    import CheckpointWatcher._
    private[this] var currentNotification: Option[UUID] = None
    private[this] val checkpointInfo = checkpoints(siteId)
    private[this] var currentState: State = Starting
    private[this] var currentTimestamp: Long = -1
    private[this] var currentRecheckTimer: Option[Cancellable] = None

    private[this] def alert(severity: Severity.Severity, message: String, icon: String): Unit = {
      currentNotification.foreach(Alerts.cancelAlert)
      currentNotification = Some(Alerts.sendAlert(Message(Checkpoint, severity, title = checkpointInfo.name, body = message,
        url   = Some(checkpointInfo.coordinates.url), icon = Some(icon))))
    }

    private[this] def alertDuration(severity: Severity.Severity, duration: FiniteDuration, icon: String): Unit =
      alert(severity, s"Site has been unresponsive for ${duration.toCoarsest}", icon)

    private[this] def scheduleRecheck(nextDuration: FiniteDuration, currentDuration: FiniteDuration): Unit = {
      currentRecheckTimer.foreach(_.cancel())
      currentRecheckTimer =
        if (nextDuration > currentDuration)
          Some(context.system.scheduler.scheduleOnce(
            nextDuration - currentDuration,
            self, Recheck(currentTimestamp)
          ))
        else
          None
    }

    private[this] def checkTimestamp(ts: Long): Unit = {
      if (ts == -1 || ts >= currentTimestamp) {
        val oldState = currentState
        val elapsed = FiniteDuration((System.currentTimeMillis() - ts).max(0L), TimeUnit.MILLISECONDS)
        currentState = if (ts == -1) Inactive else timestampToState(elapsed)
        currentTimestamp = ts
        (oldState, currentState) match {
          case (before, after) if before == after ⇒
          case (Starting, _)                      ⇒
          case (Inactive, Up) ⇒
            alert(Severity.Verbose, "Site went up for the first time", Glyphs.beatingHeart)
          case (_, Notice) ⇒
            alertDuration(Severity.Info, noticeDelay, Glyphs.brokenHeart)
          case (_, Warning) ⇒
            alertDuration(Severity.Warning, warningDelay, Glyphs.brokenHeart)
          case (_, Critical) ⇒
            alertDuration(Severity.Critical, criticalDelay, Glyphs.brokenHeart)
          case (_, Up) ⇒
            alert(Severity.Info, "Site is back up", Glyphs.growingHeart)
          case (_, Inactive) ⇒
            alert(Severity.Critical, "Site info has disappeared from database", Glyphs.skullAndCrossbones)
          case (_, _) ⇒
            log.error("Impossible checkpoint state transition from {} to {}", oldState, currentState)
        }
        currentState match {
          case Up      ⇒ scheduleRecheck(noticeDelay, elapsed)
          case Notice  ⇒ scheduleRecheck(warningDelay, elapsed)
          case Warning ⇒ scheduleRecheck(criticalDelay, elapsed)
          case _       ⇒
        }
        if (currentState == Inactive)
          PingState.removePing(siteId)
        else
          PingState.setLastPing(siteId, ts)

      }
    }

    def receive = {
      case Initial ⇒
        sender() ! Ack

      case ts: Long ⇒
        checkTimestamp(ts)
        sender() ! Ack

      case Recheck(ts) ⇒
        if (ts == currentTimestamp)
          checkTimestamp(ts)

      case Complete ⇒
        log.error("CheckpointWatcher for site {} has terminated on complete", siteId)
        context.stop(self)

      case Failure(t) ⇒
        log.error(t, "CheckpointWatcher for site {} has terminated on failure", siteId)
        context.stop(self)
    }

  }

  private object CheckpointWatcher {
    case object Initial
    case object Ack
    case object Complete
    case class Recheck(ts: Long)

    private def timestampToState(elapsed: FiniteDuration): State =
      if (elapsed >= criticalDelay)
        Critical
      else if (elapsed >= warningDelay)
        Warning
      else if (elapsed >= noticeDelay)
        Notice
      else
        Up
  }

  /**
   * Return the timestamp corresponding to the last proof of live of a site.
   */
  private def lastPing(siteId: Int @@ SiteId, database: Database)(implicit ec: ExecutionContext): Future[Option[Long]] =
    database.view[Int, JsObject]("admin", "alive",
      Seq("startkey" → SiteId.unwrap(siteId).toString, "endkey" → SiteId.unwrap(siteId).toString, "group" → "true")).map { rows ⇒
        rows.headOption.map(row ⇒ (row._2 \ "max").as[Long])
      }

  private val siteRegex = """(checkpoints|ping)-(\d+)-.*""".r

  private def docToSite(js: JsObject): Option[Int] =
    siteRegex.findFirstMatchIn((js \ "_id").as[String]).map(_.group(2).toInt)

  private def docToMaxTimestamp(siteId: Int @@ SiteId, database: Database): Flow[JsObject, Long, NotUsed] =
    Flow[JsObject].flatMapConcat { doc ⇒
      if ((doc \ "_deleted").asOpt[Boolean].contains(true))
        Source.fromFuture(lastPing(siteId, database).map(_.getOrElse(-1)))
      else
        (doc \ "type").asOpt[String] match {
          case Some("ping") ⇒
            Source.single((doc \ "time").as[Long])
          case Some("checkpoint") ⇒
            val times = (doc \ "times").asOpt[List[Long]].getOrElse(Nil)
            val artificialTimes = (doc \ "artificial_times").asOpt[List[Long]].getOrElse(Nil)
            val realTimes = times.diff(artificialTimes)
            realTimes.lastOption match {
              case Some(ts) ⇒ Source.single(ts)
              case None     ⇒ Source.empty[Long]
            }
          case _ ⇒
            Source.empty[Long]
        }
    }

  private def pingAlerts(database: Database)(implicit context: ActorRefFactory): RunnableGraph[Future[Done]] = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore, Sink.ignore) { case (s1, s2) ⇒ s1 } { implicit b ⇒ (sink, s2) ⇒
    import CheckpointWatcher._
    import GraphDSL.Implicits._

    val sites = checkpoints.size
    val in = database.changesSource(Map("filter" → "admin/liveness-info", "include_docs" → "true"))
    val partition = b.add(Partition[JsObject](sites + 1, docToSite(_) match {
      case Some(n) ⇒ n
      case None    ⇒ sites
    }))

    in ~> Flow[JsObject].map(js ⇒ (js \ "doc").as[JsObject]) ~> partition
    for (siteId ← 0 until sites) {
      val actorRef = context.actorOf(Props(new CheckpointWatcher(SiteId(siteId), database)))
      partition.out(siteId) ~> Flow[JsObject].prepend(Source.single(Json.obj("_deleted" → true))) ~>
        docToMaxTimestamp(SiteId(siteId), database) ~> Sink.actorRefWithAck[Long](actorRef, Initial, Ack, Complete)
    }
    partition.out(sites) ~> sink
    ClosedShape
  })

  def runPingAlerts(database: Database)(implicit context: ActorRefFactory) =
    pingAlerts(database).run()(ActorMaterializer.create(context))

}
