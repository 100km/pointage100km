package replicate.alerts

import akka.stream.ActorFlowMaterializer
import net.rfc1149.canape.Database
import play.api.libs.json.JsString
import replicate.utils.{Global, PeriodicTaskActor}

import scala.concurrent.Future

class RaceRanking(database: Database, raceId: Int) extends PeriodicTaskActor {

  private[this] implicit val dispatcher = context.system.dispatcher
  private[this] implicit val fm = ActorFlowMaterializer()

  override def period = Global.RaceRanking.alertInterval

  private[this] val raceName = Global.infos.flatMap(_.races.get(raceId)).fold(s"Unnamed race $raceId")(_.name)

  // null means that we do not read any value yet
  private[this] var currentHead: Seq[Int] = null

  override def immediateStart = true

  override def preStart() = {
    super.preStart()
    log.debug(s"""Launched race ranking alert service for race "$raceName"""")
  }

  private[this] def info(message: String): Future[Unit] = {
    val decorated = s"$raceName: $message"
    log.info(decorated)
    Alerts.deliverAlert(decorated).map(_ => ())
  }

  private[this] def contestantInfo(bib: Int): Future[String] =
    database(s"contestant-$bib") map { doc =>
      val (JsString(firstName), JsString(lastName)) = (doc \ "first_name", doc \ "name")
      s"$firstName $lastName (bib $bib)"
    }

  private[this] def checkForChange(runners: Seq[Int]): Future[Unit] = {
    val fs = for ((bib, idx) <- runners.take(Global.RaceRanking.topRunners).zipWithIndex; ranking = idx + 1)
      yield {
        Some(currentHead.indexOf(bib)).filterNot(_ == -1) match {
          case Some(previousRanking) if ranking < previousRanking =>
            contestantInfo(bib).flatMap(name => info(s"$name is now ranked $ranking (previously $previousRanking)"))
          case None if currentHead.size == Global.RaceRanking.topRunners =>
            contestantInfo(bib).flatMap(name => info(s"$name just appeared at rank $ranking"))
          case None =>
            contestantInfo(bib).flatMap(name => info(s"$name is now ranked $ranking (initial ranking)"))
          case _ =>
            Future.successful(())
        }
      }
    currentHead = runners
    Future.sequence(fs).map(_ => ())
  }

  override def future = Ranking.headOfRace(raceId, database, Global.RaceRanking.previousRanking).flatMap(runners =>
    if (currentHead == null) Future { currentHead = runners } else checkForChange(runners))

}
