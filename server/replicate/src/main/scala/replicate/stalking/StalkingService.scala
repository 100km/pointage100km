package replicate.stalking

import akka.NotUsed
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props, Stash, Terminated}
import akka.pattern.pipe
import akka.stream.scaladsl.Sink
import net.rfc1149.canape.Database
import play.api.libs.json.{JsObject, Json}
import replicate.alerts.Alerts
import replicate.messaging
import replicate.messaging.Message
import replicate.messaging.Message.{Severity, TextMessage}
import replicate.scrutineer.Analyzer.ContestantAnalysis
import replicate.state.ContestantState
import replicate.utils.Types._
import replicate.utils.{FormatUtils, Global, Glyphs}
import scalaz.@@

/**
 * This class will keep the list of stalkers for every contestant up-to-date, and will also maintain
 * the information on the last status for which a text message has been sent for a contestant.
 */
class StalkingService(database: Database, textService: ActorRef) extends Actor with Stash with ActorLogging {

  import StalkingService._
  import context.dispatcher

  // Map of contestant to signalled distance
  private[this] var stalkingInfo: Map[Int @@ ContestantId, Double] = Map()

  override def preStart = {
    context.watch(textService)
    context.become(receiveInitial)
    database.view[Int @@ ContestantId, JsObject]("replicate", "sms-distance", List("group" → "true")).map(_.map {
      case (k, v) ⇒ (k, (v \ "max").as[Double])
    }).transform(InitialNotifications, InitialFailure).pipeTo(self)
  }

  private[this] def sendToStalkers(analysis: ContestantAnalysis) = {
    val contestantId = analysis.contestantId
    ContestantState.contestantFromId(contestantId) match {
      case Some(contestant) ⇒
        if (contestant.stalkers.nonEmpty) {
          val point = analysis.after.last
          if (System.currentTimeMillis() - point.timestamp <= Global.TextMessages.maxAcceptableDelay.toMillis) {
            // Check that we didn't already send information about this contestant for this distance or a greater one.
            val distanceStr = FormatUtils.formatDistance(point.distance)
            if (stalkingInfo.get(analysis.contestantId).exists(_ >= point.distance))
              log.info(
                "Skipping information for {} in race {} at distance {} (sent for distance {} already)",
                contestant.full_name_and_bib, analysis.raceId, distanceStr,
                FormatUtils.formatDistance(stalkingInfo(contestantId)))
            else {
              stalkingInfo += analysis.contestantId → point.distance
              val message = s"${contestant.full_name_and_bib} : passage à ${FormatUtils.formatDate(point.timestamp, withSeconds = true)} " +
                s"""au site "${Global.infos.get.checkpoints(point.siteId).name}" (tour ${point.lap}, $distanceStr)"""
              contestant.stalkers.foreach(textService ! (_, message))
              database.insert(Json.obj("type" → "sms", "bib" → contestantId, "distance" → point.distance,
                "timestamp" → System.currentTimeMillis(), "recipients" → contestant.stalkers,
                "message" → message, "_id" → s"sms-$contestantId-${point.distance}"))
            }
          } else
            log.info(
              "Not sending obsolete (older than {}) checkpoint information for {} in race {}",
              Global.TextMessages.maxAcceptableDelay.toCoarsest, contestant.full_name_and_bib, analysis.raceId)
        }
      case None ⇒
        log.warning("no information known on contestant {}", analysis.contestantId)
    }
  }

  def receiveInitial: Receive = {

    case InitialNotifications(notifications) ⇒
      stalkingInfo = notifications.toMap
      unstashAll()
      context.become(receive)
      log.info("Stalking service starting with existing information")

    case Failure(InitialFailure(throwable)) ⇒
      log.error(throwable, "could not get initial notifications state, aborting")
      throw throwable

    case _ ⇒
      stash()
  }

  def receive = {

    case OnInit ⇒
      // Request an initial analysis
      sender() ! Ack

    case analysis: ContestantAnalysis ⇒
      // Request another analysis
      sender() ! Ack
      // Do not send an empty analysis (last point removed)
      if (analysis.after.nonEmpty)
        sendToStalkers(analysis)

    case OnComplete ⇒
      log.info("end of stream, terminating")
      context.stop(self)

    case Failure(throwable: Throwable) ⇒
      log.error(throwable, "stream terminating on error")
      context.stop(self)

    case Terminated(`textService`) ⇒
      Alerts.sendAlert(messaging.Message(TextMessage, Severity.Critical, "No stalker service",
        "Text service actor has terminated, stalker service will not run", icon = Some(Glyphs.telephoneReceiver)))
      log.error("No text service is available, stopping StalkingService actor")
      context.stop(self)

    case other ⇒
      log.warning(s"Received $other")
  }

}

object StalkingService {

  private case class InitialNotifications(notifications: Seq[(Int @@ ContestantId, Double)])
  private case class InitialFailure(throwable: Throwable) extends Exception

  private case object Ack
  private case object OnInit
  private case object OnComplete

  def stalkingServiceSink(database: Database, textService: ActorRef)(implicit context: ActorRefFactory): Sink[ContestantAnalysis, NotUsed] = {
    val actorRef = context.actorOf(Props(new StalkingService(database, textService)), "stalking")
    Sink.actorRefWithAck[ContestantAnalysis](actorRef, OnInit, Ack, OnComplete)
  }
}

