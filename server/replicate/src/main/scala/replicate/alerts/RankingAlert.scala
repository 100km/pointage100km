package replicate.alerts

import akka.stream.{ActorFlowMaterializer, FlowMaterializer}
import net.rfc1149.canape.{Couch, Database}
import play.api.libs.json.{JsString, JsValue, Json}
import replicate.messaging.Message
import replicate.messaging.Message.Severity.Severity
import replicate.messaging.Message.{RaceInfo, Severity}
import replicate.utils.Infos.RaceInfo
import replicate.utils.{Global, PeriodicTaskActor}

import scala.concurrent.{ExecutionContext, Future}

class RankingAlert(database: Database, raceInfo: RaceInfo) extends PeriodicTaskActor {

  import RankingAlert._

  private[this] implicit val dispatcher = context.system.dispatcher
  private[this] implicit val fm = ActorFlowMaterializer()

  override val period = Global.RankingAlerts.checkInterval
  override def immediateStart = true

  // null means that there has been no update yet
  private[this] var currentHead: Seq[Int] = null

  private[this] def alert(severity: Severity, rank: Int, message: String, addLink: Boolean): Unit =
    Alerts.sendAlert(Message(RaceInfo, severity, title = s"${raceInfo.name}, rank $rank",
      body = message, url = if (addLink) Global.configuration.map(_.adminLink) else None))

  private[this] def contestantInfo(bib: Int): Future[String] =
    database(s"contestant-$bib") map { doc =>
      val (JsString(firstName), JsString(lastName)) = (doc \ "first_name", doc \ "name")
      s"$firstName $lastName (bib $bib)"
    }

  private[this] def checkForChange(runners: Seq[Int]): Future[Unit] = {
    val fs = for ((bib, idx) <- runners.zipWithIndex; ranking = idx + 1)
      yield {
        val isAtHead = ranking <= Global.RankingAlerts.topRunners
        Some(currentHead.indexOf(bib)).filterNot(_ == -1).map(_+1) match {
          // Someone gained many ranks at once
          case Some(previousRanking) if ranking < previousRanking && previousRanking - ranking >= Global.RankingAlerts.suspiciousRankJump =>
            contestantInfo(bib).map(name =>
              alert(Severity.Warning, ranking, s"$name gained ${previousRanking - ranking} ranks at once (was at rank $previousRanking)", addLink = true))
          // Someone did progress into the top-runners
          case Some(previousRanking) if isAtHead && ranking < previousRanking =>
            val suspicious = previousRanking >= Global.RankingAlerts.headOfRace
            contestantInfo(bib).map(name => alert(if (suspicious) Severity.Warning else Severity.Info, ranking,
              s"$name was previously at rank $previousRanking (${ranking-previousRanking})", addLink = suspicious))
          // Someone appeared at the head of the race while we did not know them previously and we know the top runners already
          case None if isAtHead && currentHead.size >= Global.RankingAlerts.topRunners =>
            contestantInfo(bib).map(name => alert(Severity.Critical, ranking, s"$name suddenly appeared to the head of the race", addLink = true))
          // Someone appeared at the head of the race, but we are still building the top runners list
          case None if isAtHead =>
            contestantInfo(bib).map(name => alert(Severity.Verbose, ranking, s"$name is at the head (initial ranking)", addLink = false))
          case _ =>
            Future.successful(())
        }
      }
    Future.sequence(fs).map(_ => currentHead = runners)
  }

  override def future = headOfRace(raceInfo, database).flatMap(runners =>
    if (currentHead == null) Future { currentHead = runners } else checkForChange(runners))

}

object RankingAlert {

  /**
   * Return the ranking of a given race.
   *
   * @return a list of bibs ordered by rank
   */
  private def headOfRace(raceInfo: RaceInfo, database: Database)(implicit fm: FlowMaterializer, ec: ExecutionContext): Future[Seq[Int]] = {
    val response = database.list("main_display", "global-ranking", "global-ranking",
      Seq("startkey" -> Json.stringify(Json.arr(raceInfo.raceId, -raceInfo.laps)), "endkey" -> Json.stringify(Json.arr(raceInfo.raceId + 1))))
    response.filter(_.status.isSuccess()).flatMap(r => Couch.jsonUnmarshaller[JsValue]().apply(r.entity)).map { result =>
      (result \ "rows").as[Array[JsValue]].headOption.map(_ \ "contestants" \\ "id" map { id =>
        // id is of the form checkpoints-CHECKPOINT-CONTESTANT
        id.as[String].split('-').last.toInt
      }).getOrElse(Seq())
    }
  }

}
