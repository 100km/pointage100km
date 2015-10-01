package replicate.alerts

import akka.http.scaladsl.model.HttpResponse
import akka.stream.{Materializer, ActorMaterializer}
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
  private[this] implicit val fm = ActorMaterializer()

  override val period = Global.RankingAlerts.checkInterval
  override def immediateStart = true

  // null means that there has been no update yet
  private[this] var currentHead: Seq[Int] = null

  /**
   * Send an alert after prepending the contestant name to the body.
   */
  private[this] def alert(severity: Severity, bib: Int, rank: Int, message: String, addLink: Boolean): Future[Unit] = {
    val contestantInfo = database(s"contestant-$bib") map { doc =>
      val (firstName, lastName) = ((doc \ "first_name").as[String], (doc \ "name").as[String])
      s"$firstName $lastName (bib $bib)"
    }
    contestantInfo.map { name =>
      Alerts.sendAlert(Message(RaceInfo, severity, title = s"${raceInfo.name}, rank $rank",
        body = s"$name $message", url = if (addLink) Global.configuration.map(_.adminLink) else None))
    }
  }

  private[this] def checkForChange(runners: Seq[Int]): Unit = {
    for ((bib, idx) <- runners.zipWithIndex; ranking = idx + 1) {
      val isAtHead = ranking <= Global.RankingAlerts.topRunners
      Some(currentHead.indexOf(bib)).filterNot(_ == -1).map(_+1) match {
        // Someone gained many ranks at once
        case Some(previousRanking) =>
          if (ranking < previousRanking && previousRanking - ranking >= Global.RankingAlerts.suspiciousRankJump) {
            // Someone gained many ranks at once
            alert(Severity.Warning, bib, ranking,
              s"gained ${previousRanking - ranking} ranks at once (was at rank $previousRanking)", addLink = true)
          } else if (isAtHead && ranking < previousRanking) {
            // Someone did progress into the top-runners
            val suspicious = previousRanking >= Global.RankingAlerts.headOfRace
            alert(if (suspicious) Severity.Warning else Severity.Info, bib, ranking,
              s"was previously at rank $previousRanking (${ranking - previousRanking})", addLink = suspicious)
          }
        case None if isAtHead =>
          if (currentHead.size >= Global.RankingAlerts.topRunners) {
            // Someone appeared at the head of the race while we did not know them previously and we know the top runners already
            alert(Severity.Critical, bib, ranking, s"suddenly appeared to the head of the race", addLink = true)
          } else {
            // Someone appeared at the head of the race, but we are still building the top runners list
            alert(Severity.Verbose, bib, ranking, s"is at the head (initial ranking)", addLink = false)
          }
        case _ =>
          Future.successful(())
      }
    }
    currentHead = runners
  }

  override def future = headOfRace(raceInfo, database).map { runners =>
    if (currentHead == null)
      currentHead = runners
    else
      checkForChange(runners)
  }

}

object RankingAlert {

  def raceRanking(raceInfo: RaceInfo, database: Database): Future[HttpResponse] =
    database.list("main_display", "global-ranking", "global-ranking",
      Seq("startkey" -> Json.stringify(Json.arr(raceInfo.raceId, -raceInfo.laps)), "endkey" -> Json.stringify(Json.arr(raceInfo.raceId + 1))))

  /**
   * Return the ranking of a given race.
   *
   * @return a list of bibs ordered by rank
   */
  private def headOfRace(raceInfo: RaceInfo, database: Database)(implicit fm: Materializer, ec: ExecutionContext): Future[Seq[Int]] = {
    raceRanking(raceInfo, database).filter(_.status.isSuccess()).flatMap(r => Couch.jsonUnmarshaller[JsValue]().apply(r.entity)).map { result =>
      (result \ "rows").as[Array[JsValue]].headOption.map(_ \ "contestants" \\ "id" map { id =>
        // id is of the form checkpoints-CHECKPOINT-CONTESTANT
        id.as[String].split('-').last.toInt
      }).getOrElse(Seq())
    }
  }

}
