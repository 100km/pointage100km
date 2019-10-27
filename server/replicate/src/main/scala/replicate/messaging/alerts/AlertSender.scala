package replicate.messaging.alerts

import java.util.UUID

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import net.rfc1149.canape.Database
import play.api.libs.json._
import replicate.messaging.Message
import replicate.utils.Global

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Actor in charge of delivering an alert and handling its immediate or future cancellation.
 *
 * @param database the database in which the alert and the cancellation information will be persisted
 * @param message the message
 * @param uuid the unique ID to use when cancelling the message
 * @param officers the officers to deliver the message to
 */
class AlertSender(context: ActorContext[AlertSender.Protocol], parent: ActorRef[AlertSender.Persisted], database: Database, message: Message, uuid: UUID, officers: Map[String, ActorRef[Messaging.Protocol]]) extends AbstractBehavior[AlertSender.Protocol] {

  import AlertSender._
  import Global.dispatcher

  /**
   * Transport-dependent ids to use to cancel the message for a given officer. Only unused cancellation ids are
   * stored here, they are removed as soon as they have been used.
   */
  private[this] var cancellationIds: Seq[(String, String)] = Seq.empty

  /**
   * Number of (positive or negative) delivery confirmations still to arrive.
   */
  private[this] var missingConfirmations: Int = 0

  /**
   * Message delivery time.
   */
  private[this] val addedTimestamp: Long = System.currentTimeMillis()

  /**
   * Message cancellation time if any. None means that the message has not been cancelled yet.
   */
  private[this] var cancelledTimestamp: Option[Long] = None

  /**
   * Targets of the message, to be saved in the document.
   */
  private[this] var targets: Seq[String] = Seq.empty

  /**
   * True if the information has been persisted in the database already. From this point on, cancellation
   * information will go through the database.
   */
  private[this] var persisted: Boolean = false

  private[this] def cancelled = cancelledTimestamp.isDefined

  // Determine officers for this message
  officersFor(database, message).foreach(officers => context.self ! Officers(officers))

  override def onMessage(msg: Protocol) = msg match {

    case Officers(targetOfficers) =>
      context.log.debug("Got targets for {}: {}", message, targetOfficers)
      targets = targetOfficers.intersect(officers.keys.toSeq)
      // Do not send the message if it has been cancelled already
      if (!cancelled) {
        missingConfirmations = targets.size
        targets.foreach(officerId => officers(officerId) ! Messaging.Deliver(message, officerId, context.messageAdapter {
          case Messaging.DeliveryReceipt(response, officerId, deliveredBy) => DeliveryReceipt(response, officerId, deliveredBy)
        }))
      }
      if (missingConfirmations == 0)
        context.self ! Write
      Behaviors.same

    case DeliveryReceipt(response, officerId, deliveredBy) =>
      // Receive delivery information for an officer
      context.log.debug("confirmation for {} received ({}): {}", officerId, response, message)
      response match {
        case Failure(t) =>
          context.log.warning("cannot send to {}: {}", officerId, message)
        case Success(Some(cancellationId)) =>
          if (cancelled)
            // Cancel delivery immediately as the message has been cancelled
            cancelAlert(deliveredBy, cancellationId)
          else
            // Store cancellation information for later
            cancellationIds :+= (officerId, cancellationId)
        case Success(None) =>
        // Do nothing, the transport does not allow cancellation
      }
      missingConfirmations -= 1
      if (missingConfirmations == 0)
        context.self ! Write
      Behaviors.same

    case Write =>
      // The documentation with the delivery and cancellation information can be persisted to the database.
      val jsonCancellationIds = JsArray(cancellationIds.map {
        case (officerId, cancellationId) => Json.obj("officer" -> officerId, "cancellation" -> cancellationId)
      })
      val doc = Json.obj("type" -> "alert", "addedTS" -> addedTimestamp, "cancellations" -> jsonCancellationIds,
        "targets" -> JsArray(targets.map(JsString))) ++
        Json.toJson(message).as[JsObject] ++
        JsObject(cancelledTimestamp.map(ts => ("cancelledTS", JsNumber(ts))).toSeq)
      context.log.debug("writing to database with id {}: {}", uuidToId(uuid), doc)
      database.insert(doc, uuidToId(uuid)).foreach(_ => context.self ! Stored)
      Behaviors.same

    case Stored =>
      persisted = true
      parent ! Persisted(uuid, context.self)
      // The document has been persisted. If a cancellation has happened between the write and the persisted phases,
      // we will start a database-based cancellation even though the cancellation ids are available since we need
      // to update the document in the database.
      if (cancelled && cancellationIds.nonEmpty)
        cancelPersisted(database, officers, uuid)
      Behaviors.same

    case Cancel =>
      context.log.debug("cancelling {}", message)
      cancelledTimestamp = Some(System.currentTimeMillis())
      if (persisted)
        // The message is persisted already, start a database-based cancellation
        cancelPersisted(database, officers, uuid)
      else if (missingConfirmations > 0) {
        // The message is not yet being written into the database as we are still waiting on confirmations. We will
        // cancel the already sent deliveries, others will be cancelled as the confirmations arrive.
        cancellationIds.foreach { case (officerId, cancellationId) => cancelAlert(officers(officerId), cancellationId) }
        cancellationIds = Seq.empty
      }
      Behaviors.same

    case Stop =>
      Behaviors.stopped
  }

}

object AlertSender {

  import Global.system.dispatcher

  private def uuidToId(uuid: UUID): String = s"alert-$uuid"

  def apply(parent: ActorRef[Persisted], database: Database, message: Message, uuid: UUID, officers: Map[String, ActorRef[Messaging.Protocol]]): Behavior[Protocol] =
    Behaviors.setup(context => new AlertSender(context, parent, database, message, uuid, officers))

  /**
   * Cancel an alert based on the information stored in the database.
   *
   * @param database the database in which cancellation information is stored
   * @param officers the mapping of officers actors which will handle the cancellation messages
   * @param uuid the unique identifier of the message to cancel
   */
  def cancelPersisted(database: Database, officers: Map[String, ActorRef[Messaging.Protocol]], uuid: UUID): Unit = {
    for (doc <- database(uuidToId(uuid))) {
      for (
        cancellation <- (doc \ "cancellations").asOpt[Array[JsObject]].getOrElse(Array());
        officerId = (cancellation \ "officer").as[String];
        cancellationId = (cancellation \ "cancellation").as[String]
      ) cancelAlert(officers(officerId), cancellationId)
      database.insert(doc - "cancellations" ++ Json.obj("cancelledTS" -> System.currentTimeMillis()))
    }
  }

  private def cancelAlert(officer: ActorRef[Messaging.Protocol], cancellationId: String): Unit =
    officer ! Messaging.Cancel(cancellationId)

  private def officersFor(database: Database, message: Message): Future[Seq[String]] = {
    val key = Json.stringify(Json.arr(message.category.toString, message.severity.toString.toLowerCase))
    database.view[JsValue, String]("admin", "officers", Seq("startkey" -> key, "endkey" -> key)).map(_.map(_._2))
  }

  sealed trait Protocol
  private case class Officers(targetOfficers: Seq[String]) extends Protocol
  private case class DeliveryReceipt(response: Try[Option[String]], officerId: String, deliveredBy: ActorRef[Messaging.Protocol]) extends Protocol
  private case object Write extends Protocol
  private case object Stored extends Protocol
  case object Cancel extends Protocol
  case object Stop extends Protocol

  case class Persisted(uuid: UUID, persistedBy: ActorRef[Protocol])
}
