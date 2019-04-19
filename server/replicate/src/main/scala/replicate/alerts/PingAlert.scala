package replicate.alerts

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl._
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Flow, GraphDSL, Partition, RunnableGraph, Sink, Source}
import akka.stream.typed.scaladsl._
import akka.{Done, NotUsed}
import net.rfc1149.canape.Database
import play.api.libs.json.{JsObject, Json}
import replicate.messaging
import replicate.messaging.Message.{Checkpoint, Severity}
import replicate.state.PingState
import replicate.utils.Global.CheckpointAlerts._
import replicate.utils.Types.SiteId
import replicate.utils._
import scalaz.@@

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object PingAlert {

  private lazy val checkpoints = Global.infos.get.checkpoints

  private implicit val dispatcher = Global.system.dispatcher

  private case object RecheckTimer

  sealed trait State
  case object Starting extends State
  case object Inactive extends State
  case object Up extends State
  case object Notice extends State
  case object Warning extends State
  case object Critical extends State

  def checkpointWatcher(siteId: Int @@ SiteId, database: Database): Behavior[CheckpointWatcher.Protocol] = Behaviors.setup { context ⇒
    Behaviors.withTimers { timers ⇒

      import CheckpointWatcher._

      var currentNotification: Option[UUID] = None
      val checkpointInfo = checkpoints(siteId)
      var currentState: State = Starting
      var currentTimestamp: Long = -1

      def alert(severity: Severity.Severity, message: String, icon: String): Unit = {
        currentNotification.foreach(Alerts.cancelAlert)
        currentNotification = Some(Alerts.sendAlert(messaging.Message(Checkpoint, severity, title = checkpointInfo.name, body = message,
                                                                      url   = Some(checkpointInfo.coordinates.url), icon = Some(icon))))
      }

      def alertDuration(severity: Severity.Severity, duration: FiniteDuration, icon: String): Unit =
        alert(severity, s"Site has been unresponsive for ${duration.toCoarsest}", icon)

      def scheduleRecheck(nextDuration: FiniteDuration, currentDuration: FiniteDuration): Unit = {
        timers.cancel(RecheckTimer)
        if (nextDuration > currentDuration)
          timers.startSingleTimer(
            RecheckTimer,
            Recheck(currentTimestamp),
            nextDuration - currentDuration)
      }

      def checkTimestamp(ts: Long): Unit = {
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
              context.log.error("Impossible checkpoint state transition from {} to {}", oldState, currentState)
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

      Behaviors.receiveMessage {
        case Initial(ackTo) ⇒
          ackTo ! Ack
          Behaviors.same

        case TimeStamp(ackTo, ts) ⇒
          checkTimestamp(ts)
          ackTo ! Ack
          Behaviors.same

        case Recheck(ts) ⇒
          if (ts == currentTimestamp)
            checkTimestamp(ts)
          Behaviors.same

        case Complete ⇒
          context.log.error("CheckpointWatcher for site {} has terminated on complete", siteId)
          Behaviors.stopped

        case Failure(t) ⇒
          context.log.error(t, "CheckpointWatcher for site {} has terminated on failure", siteId)
          Behaviors.stopped
      }
    }
  }

  private[alerts] object CheckpointWatcher {

    sealed trait Protocol
    case class Initial(ackTo: ActorRef[Ack.type]) extends Protocol
    case class Recheck(ts: Long) extends Protocol
    case class TimeStamp(ackTo: ActorRef[Ack.type], ts: Long) extends Protocol
    case object Complete extends Protocol
    case class Failure(ex: Throwable) extends Protocol

    object Ack

    def timestampToState(elapsed: FiniteDuration): State =
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

  private def pingAlerts(database: Database)(implicit context: ActorContext[_]): RunnableGraph[Future[Done]] = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit b ⇒ sink ⇒
    import CheckpointWatcher._
    import akka.stream.scaladsl.GraphDSL.Implicits._

    val sites = checkpoints.size
    val in = b.add(database.changesSource(Map("filter" → "admin/liveness-info", "include_docs" → "true")))
    val partition = b.add(Partition[JsObject](sites + 1, docToSite(_) match {
      case Some(n) ⇒ n
      case None    ⇒ sites
    }))

    in ~> Flow[JsObject].map(js ⇒ (js \ "doc").as[JsObject]) ~> partition
    for (siteId ← 0 until sites) {
      val actorRef: ActorRef[Protocol] = context.spawn(checkpointWatcher(SiteId(siteId), database), s"site-$siteId")
      val sink: Sink[Long, NotUsed] = ActorSink.actorRefWithAck(ref               = actorRef, onCompleteMessage = Complete,
                                                                onFailureMessage  = Failure.apply, messageAdapter = TimeStamp.apply,
                                                                onInitMessage     = Initial.apply, ackMessage = Ack)
      partition.out(siteId) ~> Flow[JsObject].prepend(Source.single(Json.obj("_deleted" → true))) ~>
        docToMaxTimestamp(SiteId(siteId), database) ~> sink
    }
    partition.out(sites) ~> sink
    ClosedShape
  })

  def runPingAlerts(database: Database)(implicit context: ActorContext[_]) =
    pingAlerts(database).run()(ActorMaterializer()(context.system))

}
