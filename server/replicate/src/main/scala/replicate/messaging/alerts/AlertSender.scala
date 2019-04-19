package replicate.messaging.alerts

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.pipe
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
class AlertSender(database: Database, message: Message, uuid: UUID, officers: Map[String, ActorRef]) extends Actor with ActorLogging {

  import AlertSender._
  import Global.dispatcher

  /**
   * Transport-dependent ids to use to cancel the message for a given officer. Only unused cancellation ids are
   * stored here, they are removed as soon as they have been used.
   */
  private[this] var cancellationIds: Seq[(String, String)] = Seq()

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
  private[this] var targets: Seq[String] = Seq()

  /**
   * True if the information has been persisted in the database already. From this point on, cancellation
   * information will go through the database.
   */
  private[this] var persisted: Boolean = false

  private[this] def cancelled = cancelledTimestamp.isDefined

  override def preStart() = {
    super.preStart()
    officersFor(database, message) map (('officers, _)) pipeTo self
  }

  override val receive: Receive = {
    case ('officers, targetOfficers: Seq[String] @unchecked) ⇒
      log.debug("Got targets for {}: {}", message, targetOfficers)
      targets = targetOfficers.intersect(officers.keys.toSeq)
      // Do not send the message if it has been cancelled already
      if (!cancelled) {
        missingConfirmations = targets.size
        targets.foreach(officerId ⇒ officers(officerId) ! ('deliver, message, officerId))
      }
      if (missingConfirmations == 0)
        self ! 'write

    case ('deliveryReceipt, response: Try[Option[String] @unchecked], officerId: String) ⇒
      // Receive delivery information for an officer
      log.debug("confirmation for {} received ({}): {}", officerId, response, message)
      response match {
        case Failure(t) ⇒
          log.warning("cannot send to {}: {}", officerId, message)
        case Success(Some(cancellationId)) ⇒
          if (cancelled)
            // Cancel delivery immediately as the message has been cancelled
            cancelAlert(sender(), cancellationId)
          else
            // Store cancellation information for later
            cancellationIds :+= (officerId, cancellationId)
        case Success(None) ⇒
        // Do nothing, the transport does not allow cancellation
      }
      missingConfirmations -= 1
      if (missingConfirmations == 0)
        self ! 'write

    case 'write ⇒
      // The documentation with the delivery and cancellation information can be persisted to the database.
      val jsonCancellationIds = JsArray(cancellationIds.map {
        case (officerId, cancellationId) ⇒ Json.obj("officer" → officerId, "cancellation" → cancellationId)
      })
      val doc = Json.obj("type" → "alert", "addedTS" → addedTimestamp, "cancellations" → jsonCancellationIds,
        "targets" → JsArray(targets.map(JsString))) ++
        Json.toJson(message).as[JsObject] ++
        JsObject(cancelledTimestamp.map(ts ⇒ ("cancelledTS", JsNumber(ts))).toSeq)
      log.debug("writing to database with id {}: {}", uuidToId(uuid), doc)
      pipe(database.insert(doc, uuidToId(uuid)).map(_ ⇒ 'persisted)) to self

    case 'persisted ⇒
      persisted = true
      context.parent ! ('persisted, uuid)
      // The document has been persisted. If a cancellation has happened between the write and the persisted phases,
      // we will start a database-based cancellation even though the cancellation ids are available since we need
      // to update the document in the database.
      if (cancelled && cancellationIds.nonEmpty)
        cancelPersisted(database, officers, uuid)

    case 'cancel ⇒
      log.debug("cancelling {}", message)
      cancelledTimestamp = Some(System.currentTimeMillis())
      if (persisted)
        // The message is persisted already, start a database-based cancellation
        cancelPersisted(database, officers, uuid)
      else if (missingConfirmations > 0) {
        // The message is not yet being written into the database as we are still waiting on confirmations. We will
        // cancel the already sent deliveries, others will be cancelled as the confirmations arrive.
        cancellationIds.foreach { case (officerId, cancellationId) ⇒ cancelAlert(officers(officerId), cancellationId) }
        cancellationIds = Seq()
      }
  }

}

object AlertSender {

  import Global.system.dispatcher

  private def uuidToId(uuid: UUID): String = s"alert-$uuid"

  /**
   * Cancel an alert based on the information stored in the database.
   *
   * @param database the database in which cancellation information is stored
   * @param officers the mapping of officers actors which will handle the cancellation messages
   * @param uuid the unique identifier of the message to cancel
   */
  def cancelPersisted(database: Database, officers: Map[String, ActorRef], uuid: UUID): Unit = {
    for (doc ← database(uuidToId(uuid))) {
      for (
        cancellation ← (doc \ "cancellations").asOpt[Array[JsObject]].getOrElse(Array());
        officerId = (cancellation \ "officer").as[String];
        cancellationId = (cancellation \ "cancellation").as[String]
      ) cancelAlert(officers(officerId), cancellationId)
      database.insert(doc - "cancellations" ++ Json.obj("cancelledTS" → System.currentTimeMillis()))
    }
  }

  private def cancelAlert(officer: ActorRef, cancellationId: String): Unit =
    officer ! ('cancel, cancellationId)

  private def officersFor(database: Database, message: Message): Future[Seq[String]] = {
    val key = Json.stringify(Json.arr(message.category.toString, message.severity.toString.toLowerCase))
    database.view[JsValue, String]("admin", "officers", Seq("startkey" → key, "endkey" → key)).map(_.map(_._2))
  }

}
