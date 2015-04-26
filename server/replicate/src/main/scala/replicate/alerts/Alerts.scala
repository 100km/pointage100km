package replicate.alerts

import akka.actor.{Actor, ActorLogging, Props}
import net.rfc1149.canape.Database
import replicate.utils.Global

class Alerts(database: Database) extends Actor with ActorLogging {

  override def preStart() = {
    log.info("Starting alert service")
    for (infos <- Global.infos)
      for (raceId <- infos.races.keys)
        context.actorOf(Props(new RaceRanking(database, raceId)), s"race-ranking-$raceId")
  }

  def receive = {
    case 'ignore =>
  }
}
