package replicate.alerts

import java.util.UUID

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{Actor, ActorLogging, PoisonPill}
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.rfc1149.canape.Database
import play.api.libs.json.Json
import replicate.messaging
import replicate.messaging.Message.{Administrativia, Severity}
import replicate.messaging._
import replicate.messaging.alerts.{AlertSender, Messaging}
import replicate.utils.{Global, Glyphs}

import scala.concurrent.{ExecutionContext, Future}

class Alerts(database: Database) extends Actor with ActorLogging {

  import Alerts._

  implicit val materializer = ActorMaterializer.create(context)

  import Global.dispatcher

  /**
   * Cache for alerts whose delivery actor is still alive and may be able to stop the diffusion
   * immediately.
   */
  private[this] var deliveryInProgress: Map[UUID, ActorRef[AlertSender.Protocol]] = Map()

  lazy private[this] val officers: Map[String, ActorRef[Messaging.Protocol]] =
    Global.replicateConfig.as[Map[String, Config]]("officers").collect {
      case (officerId, config) if !config.as[Option[Boolean]]("disabled").contains(true) ⇒ (officerId, startOfficerActor(officerId, config))
    }

  private[this] def startOfficerActor(officerId: String, config: Config): ActorRef[Messaging.Protocol] = {
    val actorRef = alerts.startFromConfig(officerId, config)
    log.debug("started actor for {}", officerId)
    actorRef
  }

  override def preStart() = {
    val officersStr = officers.keys.toSeq.sorted.mkString(", ")
    // Create officers documents asynchronously then send starting message
    createOfficerDocuments(database, officers.keys.toSeq).andThen {
      case _ ⇒ sendAlert(messaging.Message(Administrativia, Severity.Verbose, "Alert service starting", s"Delivering alerts to $officersStr",
                                           icon = Some(Glyphs.wrench)))
    }
    database.updateForm("bib_input", "force-update", "officers", Map("json" → Json.stringify(Json.obj("officers" → officersStr))))
    // Alert services
    PingAlert.runPingAlerts(database)
    BroadcastAlert.runBroadcastAlerts(database)
  }

  def receive = {
    case ('message, message: Message, uuid: UUID) ⇒
      // Deliver a new message through a dedicated actor
      log.debug("sending message {} with UUID {}", message, uuid)
      deliveryInProgress += uuid → context.spawnAnonymous(AlertSender(self, database, message, uuid, officers))

    case ('cancel, uuid: UUID) ⇒
      // Cancel a message either through its delivery actor if it is still active, or using stored information
      // in the database otherwise.
      log.debug("cancelling message with UUID {}", uuid)
      if (deliveryInProgress.contains(uuid))
        deliveryInProgress(uuid) ! AlertSender.Cancel
      else
        AlertSender.cancelPersisted(database, officers, uuid)

    case ('persisted, uuid: UUID) ⇒
      // When delivery and cancellation information has been persisted into the database, the delivery actor may
      // be stopped. Cancellation information will be pulled up from the database if needed later.
      log.debug("message with UUID {} persisted, removing from cache and stopping actor", uuid)
      deliveryInProgress -= uuid
      sender() ! PoisonPill
  }

}

object Alerts {

  private[this] val alertActor = Global.system.actorSelection("/user/alerts")

  /**
   * Send an alert to all officers.
   *
   * @param message the message ot send
   * @return the UUID that can be used to cancel the message on delivery methods that support it
   */
  def sendAlert(message: Message): UUID = {
    val uuid = UUID.randomUUID()
    alertActor ! ('message, message, uuid)
    uuid
  }

  /**
   * Cancel an existing alert.
   *
   * @param uuid the alert to cancel.
   */
  def cancelAlert(uuid: UUID): Unit =
    alertActor ! ('cancel, uuid)

  /**
   * Create the missing officer documents using a batch insert. The "system" officer will be set to use the debug
   * severity level while the other ones will use warning. Existing documents with the same id will not be overriden.
   *
   * @param database the database in which the insertions are done
   * @param officers the list of officer names for which a document must exist
   * @param ec an execution context
   * @return a future which will be completed once the insertions are done
   */
  private def createOfficerDocuments(database: Database, officers: Seq[String])(implicit ec: ExecutionContext): Future[Unit] = {
    val docs = officers.map(officerId ⇒ Json.obj("_id" → s"officer-$officerId", "type" → "officer", "officer" → officerId,
      "log_levels" → Json.obj("*" → (if (officerId == "system") "debug" else "warning"))))
    database.bulkDocs(docs, allOrNothing = false).map(_ ⇒ ())
  }

}
