package replicate.alerts

import net.rfc1149.canape.{Couch, Database}
import replicate.utils.{Global, PeriodicTaskActor}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class RaceRanking(database: Database, raceId: Int) extends PeriodicTaskActor {

  private[this] implicit val dispatcher = context.system.dispatcher

  override def period = Global.raceRankingAlertInterval

  private[this] val raceName = Global.infos.flatMap(_.races.get(raceId)).fold(s"Unnamed race $raceId")(_.name)

  private[this] var currentWinner: Option[Int] = None

  override def preStart() = {
    super.preStart()
    log.info(s"""Launched race ranking alert service for race "$raceName"""")
  }

  private[this] def checkForChange(winner: Option[Int]): Future[Unit] = {
    if (winner != currentWinner) {
      val winnerNameFuture =
        winner.fold(Future.successful(""))(w => database(s"contestant-$w").map(doc => s"${(doc \ "first_name").as[String]} ${(doc \ "name").as[String]} (bib $w)")
          .recover { case Couch.StatusError(404, _, _) => s"unamed contestant (bib $w)" })
      winnerNameFuture.map { winnerName =>
        (currentWinner, winner) match {
          case (None, None) =>
          case (None, Some(_)) =>
            log.info(s"""First winner ever for race "$raceName": $winnerName""")
          case (Some(_), None) =>
            log.info(s"""No more winner for race "$raceName"""")
          case (_, Some(_)) =>
            log.info(s"""First place for race "$raceName" is now for $winnerName""")
        }
        currentWinner = winner
      }
    } else
      Future.successful(())
  }

  override def future = Ranking.headOfRace(raceId, database).flatMap(checkForChange)

}
