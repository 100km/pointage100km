package replicate

import akka.Done
import akka.http.scaladsl.util.FastFuture
import org.specs2.mutable._
import org.specs2.specification.Scope
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads}
import replicate.state.RankingState
import replicate.state.RankingState.{CheckpointData, Point}
import replicate.utils.{Global, Infos}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source

class RankingStateSpec extends Specification {

  import RankingStateSpec._

  sequential

  trait CleanRanking extends Scope with BeforeAfter {
    implicit val dispatcher = Global.dispatcher
    def before() = Await.ready(RankingState.reset(), 1.second)
    def after() = Await.ready(RankingState.reset(), 1.second)
  }

  "updateTimestamps()" should {

    "acknowledge the absence of previous rank for a new contestant" in new CleanRanking {
      val result = Await.result(RankingState.updateTimestamps(CheckpointData(42, 1, 0, List(1000))), 1.second)
      result.previousRank should beNone
    }

    "register a rank for a new contestant" in new CleanRanking {
      val result = Await.result(RankingState.updateTimestamps(CheckpointData(42, 1, 0, List(1000))), 1.second)
      result.rank should be equalTo Some(1)
    }

    "return the correct rank for a later contestant" in new CleanRanking {
      Await.ready(RankingState.updateTimestamps(CheckpointData(42, 1, 0, List(1000))), 1.second)
      val result = Await.result(RankingState.updateTimestamps(CheckpointData(50, 1, 0, List(1200))), 1.second)
      result.rank should be equalTo Some(2)
    }

    "return the correct rank for a later reinserted contestant" in new CleanRanking {
      Await.ready(RankingState.updateTimestamps(CheckpointData(42, 1, 0, List(1000))), 1.second)
      val result = Await.result(RankingState.updateTimestamps(CheckpointData(50, 1, 0, List(999))), 1.second)
      result.rank should be equalTo Some(1)
    }

    "return the correct previous rank for a contestant" in new CleanRanking {
      Await.ready(RankingState.updateTimestamps(CheckpointData(42, 1, 0, List(1000))), 1.second)
      Await.ready(RankingState.updateTimestamps(CheckpointData(50, 1, 0, List(999))), 1.second)
      val result = Await.result(RankingState.updateTimestamps(CheckpointData(42, 1, 1, List(1200))), 1.second)
      result.previousRank should be equalTo Some(2)
      result.rank should be equalTo Some(1)
    }

    "correctly compute the number of laps" in new CleanRanking {
      val sites = List(0, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3, 4, 5, 6, 0, 1, 2)
      Await.ready(Future.sequence(sites.zipWithIndex.groupBy(_._1).mapValues(_.map(_._2.toLong)).map {
        case (siteId, timestamps) ⇒
          RankingState.updateTimestamps(CheckpointData(42, 1, siteId, timestamps))
      }), 1.second)
      val result = Await.result(RankingState.pointsAndRank(42, 1), 1.second)
      result.points.map(_.lap) should be equalTo Seq(1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3)
    }

    "correctly compute the number of laps with partial data" in new CleanRanking {
      val sites = List(0, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3, 4, 5, 6, 0, 1, 2, 2)
      Await.ready(Future.sequence(sites.zipWithIndex.groupBy(_._1).mapValues(_.map(_._2.toLong)).map {
        case (siteId, timestamps) ⇒
          RankingState.updateTimestamps(CheckpointData(42, 1, siteId, timestamps))
      }), 1.second)
      val result = Await.result(RankingState.pointsAndRank(42, 1), 1.second)
      result.points.map(_.lap) should be equalTo Seq(1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 4)
    }
  }

  "pointsAndRank()" should {

    "acknowledge the absence of information about a contestant" in new CleanRanking {
      val result = Await.result(RankingState.pointsAndRank(42, 1), 1.second)
      result.previousRank should beNone
      result.rank should beNone
      result.points should beEmpty
    }
  }

  "ranks()" should {

    "return the ranks in all races" in new CleanRanking {
      Await.ready(RankingState.updateTimestamps(CheckpointData(44, 1, 0, List(1050, 1350))), 1.second)
      Await.ready(RankingState.updateTimestamps(CheckpointData(44, 1, 1, List(1250, 1450))), 1.second)
      Await.ready(RankingState.updateTimestamps(CheckpointData(42, 1, 0, List(1000, 1300))), 1.second)
      Await.ready(RankingState.updateTimestamps(CheckpointData(42, 1, 1, List(1200, 1400))), 1.second)
      Await.ready(RankingState.updateTimestamps(CheckpointData(55, 2, 1, List(1400, 1600))), 1.second)
      Await.ready(RankingState.updateTimestamps(CheckpointData(55, 2, 0, List(1300, 1500))), 1.second)
      val result = Await.result(RankingState.ranks(), 1.second)
      result must be equalTo Map(1 → Seq(42, 44), 2 → Seq(55))
    }
  }

  "raceData()" should {

    "return the full checkpoints" in new CleanRanking {
      Await.ready(RankingState.updateTimestamps(CheckpointData(42, 1, 0, List(1000, 1300))), 1.second)
      Await.ready(RankingState.updateTimestamps(CheckpointData(42, 1, 1, List(1200, 1400))), 1.second)
      Await.ready(RankingState.updateTimestamps(CheckpointData(55, 2, 1, List(1400, 1600))), 1.second)
      Await.ready(RankingState.updateTimestamps(CheckpointData(55, 2, 0, List(1300, 1500))), 1.second)
      val result = Await.result(RankingState.raceData(), 1.second)
      result must be equalTo Map(
        (42, 1) → Seq(Point(0, 1000, 1), Point(1, 1200, 1), Point(0, 1300, 2), Point(1, 1400, 2)),
        (55, 2) → Seq(Point(0, 1300, 1), Point(1, 1400, 1), Point(0, 1500, 2), Point(1, 1600, 2))
      )
    }
  }

  "a full simulation" should {

    "load a full race information in a reasonable time" in {
      Await.ready(installFullRace(), 5.seconds)
      success
    }

    "get the right ranking information" in {
      // 456 did not run the loops in the right order, so they got artificially ahead
      val ranks = Await.result(RankingState.ranks(), 1.second)
      ranks(1).take(11) should be equalTo List(456, 688, 24, 201, 719, 522, 242, 241, 110, 314, 217)
      ranks(2).take(10) should be equalTo List(393, 491, 56, 490, 71, 91, 287, 670, 933, 560)
      ranks(3).take(10) should be equalTo List(879, 867, 738, 498, 280, 735, 788, 790, 701, 759)
    }
  }

}

object RankingStateSpec {

  implicit val dispatcher = Global.dispatcher

  case class CheckpointEntry(contestantId: Int, raceId: Int, siteId: Int, times: Seq[Long],
      deletedTimes: Option[Seq[Long]], artificialTimes: Option[Seq[Long]]) {
    def checkpointData = CheckpointData(contestantId, raceId, siteId, times)
  }

  implicit val checkpointEntryReads: Reads[CheckpointEntry] = (
    (JsPath \ "bib").read[Int] and
    (JsPath \ "race_id").read[Int] and
    (JsPath \ "site_id").read[Int] and
    (JsPath \ "times").read[Seq[Long]] and
    (JsPath \ "deleted_times").readNullable[Seq[Long]] and
    (JsPath \ "artificial_times").readNullable[Seq[Long]]
  )(CheckpointEntry.apply _)

  def loadRaceData: Iterator[CheckpointEntry] =
    Source.fromInputStream(classOf[ClassLoader].getResourceAsStream("/dummy-timings.txt"), "utf-8").getLines.map(Json.parse(_).as[CheckpointEntry])

  def loadInfos: Infos = Json.parse(classOf[ClassLoader].getResourceAsStream("/infos.json")).as[Infos]

  def installSoleContestant(contestantId: Int, data: Seq[CheckpointEntry]): Unit = {
    Await.ready(for (
      _ ← RankingState.reset();
      _ ← Future.sequence(data.filter(_.contestantId == contestantId).map(e ⇒ RankingState.updateTimestamps(e.checkpointData))).map(_ ⇒ Done)
    ) yield Done, 5.seconds)
  }

  def installFullRace(): Future[Done] = {
    val infos = loadInfos
    RankingState.reset()
    Future.sequence(for (entry ← loadRaceData) yield {
      if (entry.raceId > 0) {
        val checkpointData = entry.checkpointData
        RankingState.updateTimestamps(checkpointData.copy(timestamps = checkpointData.timestamps.take(infos.races_laps(checkpointData.raceId))))
      } else
        FastFuture.successful(Done)
    }).map(_ ⇒ Done)
  }

}
