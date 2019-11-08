package replicate.alerts

import java.util.UUID

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.Materializer
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

object Alerts {

  /**
   * The stash is used as cache for alerts whose delivery actor is still alive and may be able
   * to stop the diffusion immediately.
   */
  val alertsBehavior: Behavior[Protocol] = Behaviors.withStash(100) { stash =>
    Behaviors.setup[Protocol] { context =>

      implicit val materializer = Materializer(context)

      import Global.dispatcher

      def startOfficerActor(officerId: String, config: Config): ActorRef[Messaging.Protocol] = {
        val actorRef = alerts.startFromConfig(context, officerId, config)
        context.log.debug("started actor for {}", officerId)
        actorRef
      }

      Behaviors.receiveMessage {
        case Initialize(database) =>
          // Create officers documents asynchronously then send starting message
          val officers: Map[String, ActorRef[Messaging.Protocol]] =
            Global.replicateConfig.as[Map[String, Config]]("officers").collect {
              case (officerId, config) if !config.as[Option[Boolean]]("disabled").contains(true) => (officerId, startOfficerActor(officerId, config))
            }
          val officersStr = officers.keys.toSeq.sorted.mkString(", ")
          createOfficerDocuments(database, officers.keys.toSeq).andThen {
            case _ => sendAlert(messaging.Message(Administrativia, Severity.Verbose, "Alert service starting", s"Delivering alerts to $officersStr",
                                                  icon = Some(Glyphs.wrench)))
          }
          database.updateForm("bib_input", "force-update", "officers", Map("json" -> Json.stringify(Json.obj("officers" -> officersStr))))

          var deliveryInProgress: Map[UUID, ActorRef[AlertSender.Protocol]] = Map()

          val permanentBehavior: Behavior[Protocol] = Behaviors.receiveMessagePartial {

            case Msg(message, uuid) =>
              // Deliver a new message through a dedicated actor
              context.log.debug("sending message {} with UUID {}", message, uuid)
              deliveryInProgress += uuid -> context.spawnAnonymous(AlertSender(context.messageAdapter {
                case AlertSender.Persisted(uuid, persistedBy) => Persisted(uuid, persistedBy)
              }, database, message, uuid, officers))
              Behaviors.same

            case Cancel(uuid) =>
              // Cancel a message either through its delivery actor if it is still active, or using stored information
              // in the database otherwise.
              context.log.debug("cancelling message with UUID {}", uuid)
              if (deliveryInProgress.contains(uuid))
                deliveryInProgress(uuid) ! AlertSender.Cancel
              else
                AlertSender.cancelPersisted(database, officers, uuid)
              Behaviors.same

            case Persisted(uuid, persistedBy) =>
              // When delivery and cancellation information has been persisted into the database, the delivery actor may
              // be stopped. Cancellation information will be pulled up from the database if needed later.
              context.log.debug("message with UUID {} persisted, removing from cache and stopping actor", uuid)
              deliveryInProgress -= uuid
              persistedBy ! AlertSender.Stop
              Behaviors.same
          }

          // Process deferred messages
          stash.unstashAll(permanentBehavior)
          // Start alert services
          PingAlert.runPingAlerts(database)(context)
          BroadcastAlert.runBroadcastAlerts(database)(context)
          // Switch to the permanent behavior
          permanentBehavior

        case msg =>
          // Further alerts will be logged later on
          stash.stash(msg)
          Behaviors.same
      }
    }
  }

  sealed trait Protocol
  case class Initialize(database: Database) extends Protocol
  private case class Msg(message: Message, uuid: UUID) extends Protocol
  private case class Cancel(uuid: UUID) extends Protocol
  private case class Persisted(uuid: UUID, persistedBy: ActorRef[AlertSender.Protocol]) extends Protocol

  private val alertsActor = Global.system.spawn(alertsBehavior, "alerts")

  /**
   * Start the alerts service. Must be called once.
   *
   * @param database the database to use to determine the officers and log errors
   */
  def initializeAlertsService(database: Database): Unit = {
    alertsActor ! Initialize(database)
  }

  /**
   * Send an alert to all officers.
   *
   * @param message the message ot send
   * @return the UUID that can be used to cancel the message on delivery methods that support it
   */
  def sendAlert(message: Message): UUID = {
    val uuid = UUID.randomUUID()
    alertsActor ! Msg(message, uuid)
    uuid
  }

  /**
   * Cancel an existing alert.
   *
   * @param uuid the alert to cancel.
   */
  def cancelAlert(uuid: UUID): Unit =
    alertsActor ! Cancel(uuid)

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
