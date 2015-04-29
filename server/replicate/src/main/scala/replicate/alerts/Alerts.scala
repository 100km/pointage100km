package replicate.alerts

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.rfc1149.canape.Database
import replicate.messaging.{FreeMobileSMS, Messaging, PushBullet}
import replicate.utils.Global

import scala.concurrent.Future
import scala.util.{Failure, Success}

class Alerts(database: Database) extends Actor with ActorLogging {

  import Global.dispatcher

  override def preStart() = {
    log.info("Starting alert service")
    Alerts.deliverAlert("Alert service starting").andThen {
      case Success(status) => log.info(s"Status delivery: $status")
      case Failure(t)      => log.error(t, "Unable to deliver alerts")
    }
    for (infos <- Global.infos)
      for (raceId <- infos.races.keys)
        context.actorOf(Props(new RaceRanking(database, raceId)), s"race-ranking-$raceId")
  }

  def receive = {
    case 'ignore =>
  }
}

object Alerts {

  import Global.dispatcher

  val officers: List[Messaging] = {
    val officersConfig = Global.replicateConfig.as[Map[String, Config]]("officers")
    officersConfig.toList.filterNot(_._2.as[Option[Boolean]]("disabled") == Some(true)).map { case (officerId, config) =>
        config.as[String]("type") match {
          case "pushbullet"     => new PushBullet(officerId, config.as[String]("token"))
          case "freemobile-sms" => new FreeMobileSMS(officerId, config.as[String]("user"), config.as[String]("password"))
          case s                => sys.error(s"Unknown officer type $s for officer $officerId")
        }
    }
  }

  def deliverAlert(message: String): Future[Seq[(Messaging, Boolean)]] = {
    Future.sequence(officers.map(officer => officer.sendMessage(message).map(status => (officer, status))))
  }

}
