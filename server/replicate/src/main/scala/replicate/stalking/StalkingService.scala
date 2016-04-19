package replicate.stalking

import akka.actor.{ActorLogging, ActorRef, ActorRefFactory, Props, Terminated}
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor.{ActorSubscriber, OneByOneRequestStrategy}
import akka.stream.scaladsl.Sink
import net.rfc1149.canape.Database
import replicate.alerts.Alerts
import replicate.messaging.Message
import replicate.messaging.Message.{Severity, TextMessage}
import replicate.scrutineer.Analyzer.{ContestantAnalysis, EnrichedPoint}
import replicate.state.ContestantState
import replicate.utils.{FormatUtils, Global, Glyphs}

/**
 * This class will keep the list of stalkers for every contestant up-to-date, and will also maintain
 * the information on the last status for which a text message has been sent for a contestant.
 */
class StalkingService(database: Database, textService: ActorRef) extends PersistentActor with ActorSubscriber with ActorLogging {

  private implicit val fm = ActorMaterializer.create(context)
  private implicit val ec = fm.executionContext

  override def persistenceId = "stalking-service"

  override val requestStrategy = OneByOneRequestStrategy

  // List of stalkers phone numbers
  private[this] var stalkers: Map[Int, List[String]] = Map()
  // Map of contestant to (lap, siteId)
  private[this] var stalkingInfo: Map[Int, EnrichedPoint] = Map()

  override def preStart = {
    context.watch(textService)
    log.info("stalking service starting")
  }

  private[this] def sendToStalkers(analysis: ContestantAnalysis) = {
    ContestantState.contestantFromId(analysis.contestantId) match {
      case Some(contestant) ⇒
        if (contestant.stalkers.nonEmpty) {
          val point = analysis.after.last
          if (System.currentTimeMillis() - point.timestamp <= Global.TextMessages.maxAcceptableDelay.toMillis) {
            val message = s"${contestant.full_name_and_bib}: passage à ${FormatUtils.formatDate(point.timestamp)} " +
              s"""au site "${Global.infos.get.checkpoints(point.siteId).name}" (tour ${point.lap}, ${FormatUtils.formatDistance(point.distance)})"""
            contestant.stalkers.foreach(textService ! (_, message))
          } else
            log.info(
              "Not sending obsolete (older than {}) checkpoint information for {} in race {}",
              Global.TextMessages.maxAcceptableDelay, contestant.full_name_and_bib, analysis.raceId
            );
        }
      case None ⇒
        log.warning("no information known on contestant {}", analysis.contestantId)
    }
  }

  val receiveRecover: Receive = {
    case analysis: ContestantAnalysis ⇒
      stalkingInfo += analysis.contestantId → analysis.after.last
    case SnapshotOffer(_, snapshot: Map[Int, EnrichedPoint] @unchecked) ⇒
      stalkingInfo = snapshot
  }

  val receiveCommand: Receive = {

    case OnNext(analysis: ContestantAnalysis) ⇒
      // Check the info freshness and remember it
      (analysis.after.lastOption, stalkingInfo.get(analysis.contestantId)) match {

        case (Some(point), None) ⇒
          persist(analysis) { _ ⇒
            stalkingInfo += analysis.contestantId → point
            sendToStalkers(analysis)
          }

        case (Some(point), Some(previous)) if point.distance > previous.distance ⇒
          persist(analysis) { _ ⇒
            stalkingInfo += analysis.contestantId → point
            sendToStalkers(analysis)
          }

        case _ ⇒
        // Do nothing, either we got back in distance, or we removed the last valid point
      }

    case OnComplete ⇒
      log.info("end of stream, terminating")
      context.stop(self)

    case OnError(throwable: Throwable) ⇒
      log.error(throwable, "stream terminating on error")
      throw throwable

    case Terminated(`textService`) ⇒
      Alerts.sendAlert(Message(TextMessage, Severity.Critical, "No stalker service",
        "Text service actor has terminated, stalker service will not run", icon = Some(Glyphs.telephoneReceiver)))
      log.error("No text service is available, stopping StalkingService actor")
      context.stop(self)

    case other ⇒
      log.info(s"Received $other")
  }

}

object StalkingService {

  def stalkingServiceSink(database: Database, textService: ActorRef)(implicit context: ActorRefFactory) =
    Sink.actorSubscriber[ContestantAnalysis](Props(new StalkingService(database, textService)))
}

