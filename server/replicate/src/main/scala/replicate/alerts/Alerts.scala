package replicate.alerts

import akka.actor.{Actor, ActorLogging, Props}
import net.rfc1149.canape.Database
import replicate.messaging.{FreeMobileSMS, Messaging, PushBullet}
import replicate.utils.Global

import scala.concurrent.Future
import scala.util.{Success, Failure}

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

  val officers: List[Messaging] = List() // Temporary structure, auth info must not enter public repository

  def deliverAlert(message: String): Future[Seq[(Messaging, Boolean)]] = {
    Future.sequence(officers.map(officer => officer.sendMessage(message).map(status => (officer, status))))
  }

}
