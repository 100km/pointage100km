package replicate.stalking

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.scaladsl.Sink
import akka.stream.typed.scaladsl.ActorSink
import net.rfc1149.canape.Database
import play.api.libs.json.{JsObject, Json}
import replicate.messaging.sms.SMSMessage
import replicate.scrutineer.Analyzer.ContestantAnalysis
import replicate.state.ContestantState
import replicate.utils.Types._
import replicate.utils.{FormatUtils, Global}
import scalaz.@@

import scala.util.{Failure, Success}

/**
 * This class will keep the list of stalkers for every contestant up-to-date, and will also maintain
 * the information on the last status for which a text message has been sent for a contestant.
 */
object StalkingService {

  private def stalkingService(database: Database, textService: ActorRef[SMSMessage]): Behavior[StalkingProtocol] = Behaviors.setup[StalkingProtocol] { context =>

    import context.executionContext

    // Map of contestant to signalled distance
    var stalkingInfo: Map[Int @@ ContestantId, Double] = Map()

    database.view[Int @@ ContestantId, JsObject]("replicate", "sms-distance", List("group" -> "true")).map(_.map {
      case (k, v) => (k, (v \ "max").as[Double])
    }).onComplete {
      case Success(notifications) => context.self ! InitialNotifications(notifications)
      case Failure(e)             => context.self ! InitialFailure(e)
    }

    def sendToStalkers(analysis: ContestantAnalysis) = {
      val contestantId = analysis.contestantId
      ContestantState.contestantFromId(contestantId) match {
        case Some(contestant) =>
          if (contestant.stalkers.nonEmpty) {
            val point = analysis.after.last
            if (System.currentTimeMillis() - point.timestamp <= Global.TextMessages.maxAcceptableDelay.toMillis) {
              // Check that we didn't already send information about this contestant for this distance or a greater one.
              val distanceStr = FormatUtils.formatDistance(point.distance)
              if (stalkingInfo.get(analysis.contestantId).exists(_ >= point.distance))
                context.log.info(
                  "Skipping information for {} in race {} at distance {} (sent for distance {} already)",
                  contestant.full_name_and_bib, analysis.raceId, distanceStr,
                  FormatUtils.formatDistance(stalkingInfo(contestantId)))
              else {
                stalkingInfo += analysis.contestantId -> point.distance
                val message = s"${contestant.full_name_and_bib} : passage à ${FormatUtils.formatDate(point.timestamp, withSeconds = true)} " +
                  s"""au site "${Global.infos.get.checkpoints(point.siteId).name}" (tour ${point.lap}, $distanceStr)"""
                contestant.stalkers.foreach(textService ! SMSMessage(_, message))
                database.insert(Json.obj("type" -> "sms", "bib" -> contestantId, "distance" -> point.distance,
                  "timestamp" -> System.currentTimeMillis(), "recipients" -> contestant.stalkers,
                  "message" -> message, "_id" -> s"sms-$contestantId-${point.distance}"))
              }
            } else
              context.log.info(
                "Not sending obsolete (older than {}) checkpoint information for {} in race {}",
                Global.TextMessages.maxAcceptableDelay.toCoarsest, contestant.full_name_and_bib, analysis.raceId)
          }
        case None =>
          context.log.warn("no information known on contestant {}", analysis.contestantId)
      }
    }

    def initialBehavior = Behaviors.withStash[StalkingProtocol](100) { stash =>
      Behaviors.receiveMessage[StalkingProtocol] {

        case InitialNotifications(notifications) =>
          stalkingInfo = notifications.toMap
          stash.unstashAll(regularBehavior)
          context.log.info("Stalking service starting with existing information")
          regularBehavior

        case InitialFailure(throwable) =>
          context.log.error("could not get initial notifications state, aborting", throwable)
          throw throwable

        case msg =>
          stash.stash(msg)
          Behaviors.same
      }
    }

    def regularBehavior = Behaviors.receiveMessagePartial[StalkingProtocol] {
      case OnInit(ackTo) =>
        // Request an initial analysis
        ackTo ! Ack
        Behaviors.same

      case Wrapped(ackTo, analysis) =>
        // Request another analysis
        ackTo ! Ack
        // Do not send an empty analysis (last point removed)
        if (analysis.after.nonEmpty)
          sendToStalkers(analysis)
        Behaviors.same

      case OnComplete =>
        context.log.info("end of stream, terminating")
        Behaviors.stopped

      case StreamFailure(throwable: Throwable) =>
        context.log.error("stream terminating on error", throwable)
        Behaviors.stopped
    }

    initialBehavior

  }

  private sealed trait StalkingProtocol
  private case class InitialNotifications(notifications: Seq[(Int @@ ContestantId, Double)]) extends StalkingProtocol
  private case class InitialFailure(throwable: Throwable) extends StalkingProtocol
  private case class OnInit(ackTo: ActorRef[Ack.type]) extends StalkingProtocol
  private case object OnComplete extends StalkingProtocol
  private case class Wrapped(ackTo: ActorRef[Ack.type], analysis: ContestantAnalysis) extends StalkingProtocol
  private case class StreamFailure(throwable: Throwable) extends StalkingProtocol

  private case object Ack

  def stalkingServiceSink(database: Database, textService: ActorRef[SMSMessage])(implicit system: ActorSystem): Sink[ContestantAnalysis, NotUsed] = {
    val actorRef = system.spawn(stalkingService(database, textService), "stalking")
    ActorSink.actorRefWithBackpressure(actorRef, Wrapped, OnInit, Ack, OnComplete, StreamFailure)
  }
}

