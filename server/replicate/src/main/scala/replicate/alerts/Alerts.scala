package replicate.alerts

import java.util.UUID

import akka.actor._
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.rfc1149.canape.Database
import play.api.libs.json.Json
import replicate.messaging.Message.{Administrativia, Severity}
import replicate.messaging._
import replicate.utils.Global

import scala.concurrent.{ExecutionContext, Future}

class Alerts(database: Database) extends Actor with ActorLogging {

  import Alerts._

  import Global.dispatcher

  /**
   * Cache for alerts whose delivery actor is still alive and may be able to stop the diffusion
   * immediately.
   */
  private[this] var deliveryInProgress: Map[UUID, ActorRef] = Map()

  lazy private[this] val officers: Map[String, ActorRef] =
    Global.replicateConfig.as[Map[String, Config]]("officers").collect {
      case (officerId, config) if config.as[Option[Boolean]]("disabled") != Some(true) => (officerId, startFromConfig(officerId, config))
    }

  private[this] def startFromConfig(officerId: String, config: Config): ActorRef = {
    val service = config.as[String]("type")
    val props = service match {
      case "pushbullet"     => Props(new Pushbullet(config.as[String]("token"))).withDispatcher("https-messaging-dispatcher")
      case "freemobile-sms" => Props(new FreeMobileSMS(config.as[String]("user"), config.as[String]("password"))).withDispatcher("https-messaging-dispatcher")
      case "system"         => Props(new SystemLogger)
      case s                => sys.error(s"Unknown officer type $s for officer $officerId")
    }
    log.debug(s"starting actor for $officerId")
    context.actorOf(props, if (service == officerId) service else s"$service-$officerId")
  }

  private[this] def startPushbulletSMS(config: Config): Option[ActorRef] = {
    for (bearerToken <- config.as[Option[String]]("sms.bearer-token");
         userIden <- config.as[Option[String]]("sms.user-iden");
         deviceIden <- config.as[Option[String]]("sms.device-iden"))
      yield context.actorOf(Props(new PushbulletSMS(bearerToken, userIden, deviceIden)).withDispatcher("https-messaging-dispatcher"), "pushbullet-sms")
  }

  override def preStart() = {
    // Create officers documents asynchronously
    createOfficerDocuments(database, officers.keys.toSeq)
    // Alert services
    for (infos <- Global.infos; raceInfo <- infos.races.values)
      context.actorOf(Props(new RankingAlert(database, raceInfo)), s"race-ranking-${raceInfo.raceId}")
    for (infos <- Global.infos; checkpointInfo <- infos.checkpoints.values)
      context.actorOf(Props(new PingAlert(database, checkpointInfo)), s"checkpoint-${checkpointInfo.checkpointId}")
    context.actorOf(Props(new BroadcastAlert(database)), "broadcasts")
    startPushbulletSMS(Global.replicateConfig) match {
      case Some(_) =>
        log.debug("started SMS service")
      case None =>
        log.warning("no SMS service defined")
    }
    // Officers
    val officersStr = officers.keys.toSeq.sorted.mkString(", ")
    database.update("bib_input", "force-update", "officers", Map("json" -> Json.stringify(Json.obj("officers" -> officersStr))))
    sendAlert(Message(Administrativia, Severity.Verbose, "Alert service starting", s"Delivering alerts to $officersStr", None))
  }

  def receive = {
    case ('message, message: Message, uuid: UUID) =>
      // Deliver a new message through a dedicated actor
      log.debug(s"sending message $message with UUID $uuid")
      deliveryInProgress += uuid -> context.actorOf(Props(new AlertSender(database, message, uuid, officers)))

    case ('cancel, uuid: UUID) =>
      // Cancel a message either through its delivery actor if it is still active, or using stored information
      // in the database otherwise.
      log.debug(s"cancelling message with UUID $uuid")
      if (deliveryInProgress.contains(uuid))
        deliveryInProgress(uuid) ! 'cancel
      else
        AlertSender.cancelPersisted(database, officers, uuid)

    case ('persisted, uuid: UUID) =>
      // When delivery and cancellation information has been persisted into the database, the delivery actor may
      // be stopped. Cancellation information will be pulled up from the database if needed later.
      log.debug(s"message with UUID $uuid persisted, removing from cache and stopping actor")
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
    val docs = officers.map(officerId => Json.obj("_id" -> s"officer-$officerId", "type" -> "officer", "officer" -> officerId,
      "log_levels" -> Json.obj("*" -> (if (officerId == "system") "debug" else "warning"))))
    database.bulkDocs(docs, allOrNothing = false).map(_ => ())
  }

}
