package replicate.alerts

import akka.stream.ActorFlowMaterializer
import net.rfc1149.canape.Database
import play.api.libs.json.JsString
import replicate.messaging.Message.Severity.Severity
import replicate.messaging.Message.{RaceInfo, Severity}
import replicate.messaging.{Message, Messaging}
import replicate.utils.Infos.RaceInfo
import replicate.utils.{Global, PeriodicTaskActor}

import scala.concurrent.Future

class RaceRanking(database: Database, raceInfo: RaceInfo) extends PeriodicTaskActor {

  private[this] implicit val dispatcher = context.system.dispatcher
  private[this] implicit val fm = ActorFlowMaterializer()

  override def period = Global.RaceRanking.checkInterval

  // null means that there has been no update yet
  private[this] var currentHead: Seq[Int] = null

  override def immediateStart = true

  private[this] def alert(severity: Severity, rank: Int, message: String, addLink: Boolean): Future[Map[Messaging, String]] = {
    Alerts.deliverAlert(Alerts.officers, Message(RaceInfo, severity, title = s"${raceInfo.name}, rank $rank",
      body = message, url = if (addLink) Global.configuration.map(_.adminLink) else None))
  }

  private[this] def contestantInfo(bib: Int): Future[String] =
    database(s"contestant-$bib") map { doc =>
      val (JsString(firstName), JsString(lastName)) = (doc \ "first_name", doc \ "name")
      s"$firstName $lastName (bib $bib)"
    }

  private[this] def checkForChange(runners: Seq[Int]): Future[Unit] = {
    val fs = for ((bib, idx) <- runners.zipWithIndex; ranking = idx + 1)
      yield {
        val isAtHead = ranking <= Global.RaceRanking.topRunners
        Some(currentHead.indexOf(bib)).filterNot(_ == -1).map(_+1) match {
          // Someone gained many ranks at once
          case Some(previousRanking) if ranking < previousRanking && previousRanking - ranking >= Global.RaceRanking.suspiciousRankJump =>
            contestantInfo(bib).flatMap(name =>
              alert(Severity.Warning, ranking, s"$name gained ${previousRanking - ranking} ranks at once (was at rank $previousRanking)", addLink = true))
          // Someone did progress into the top-runners
          case Some(previousRanking) if isAtHead && ranking < previousRanking =>
            val suspicious = previousRanking >= Global.RaceRanking.headOfRace
            contestantInfo(bib).flatMap(name => alert(if (suspicious) Severity.Warning else Severity.Info, ranking,
              s"$name was previously at rank $previousRanking (${ranking-previousRanking})", addLink = suspicious))
          // Someone appeared at the head of the race while we did not know them previously and we know the top runners already
          case None if isAtHead && currentHead.size >= Global.RaceRanking.topRunners =>
            contestantInfo(bib).flatMap(name => alert(Severity.Critical, ranking, s"$name suddenly appeared to the head of the race", addLink = true))
          // Someone appeared at the head of the race, but we are still building the top runners list
          case None if isAtHead =>
            contestantInfo(bib).flatMap(name => alert(Severity.Verbose, ranking, s"$name is at the head (initial ranking)", addLink = false))
          case _ =>
            Future.successful(())
        }
      }
    Future.sequence(fs).map(_ => currentHead = runners)
  }

  override def future = Ranking.headOfRace(raceInfo, database).flatMap(runners =>
    if (currentHead == null) Future { currentHead = runners } else checkForChange(runners))

}
