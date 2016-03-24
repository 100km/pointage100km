package replicate

import akka.NotUsed
import akka.http.scaladsl.util.FastFuture
import org.specs2.mutable._
import org.specs2.specification.Scope
import play.api.libs.json.Json
import replicate.state.RankingState
import replicate.state.RankingState.{Point, RaceRanking}
import replicate.utils.Global

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source

class RankingStateSpec extends Specification {

  sequential

  trait CleanRanking extends Scope with BeforeAfter {
    implicit val dispatcher = Global.dispatcher
    private[this] def reset() = Await.ready(RankingState.rankingState.alter(Map[Int, RaceRanking]()), 1.second)
    def before() = reset()
    def after() = reset()
  }

  "updateTimestamps()" should {

    "acknowledge the absence of previous rank for a new contestant" in new CleanRanking {
      val result = Await.result(RankingState.updateTimestamps(42, 1, 0, List(1000)), 1.second)
      result._1 should beNone
    }

    "register a rank for a new contestant" in new CleanRanking {
      val result = Await.result(RankingState.updateTimestamps(42, 1, 0, List(1000)), 1.second)
      result._2.get._2 should be equalTo 1
    }

    "return the correct rank for a later contestant" in new CleanRanking {
      Await.ready(RankingState.updateTimestamps(42, 1, 0, List(1000)), 1.second)
      val result = Await.result(RankingState.updateTimestamps(50, 1, 0, List(1200)), 1.second)
      result._2.get._2 should be equalTo 2
    }

    "return the correct rank for a later reinserted contestant" in new CleanRanking {
      Await.ready(RankingState.updateTimestamps(42, 1, 0, List(1000)), 1.second)
      val result = Await.result(RankingState.updateTimestamps(50, 1, 0, List(999)), 1.second)
      result._2.get._2 should be equalTo 1
    }

    "return the correct previous rank for a contestant" in new CleanRanking {
      Await.ready(RankingState.updateTimestamps(42, 1, 0, List(1000)), 1.second)
      Await.ready(RankingState.updateTimestamps(50, 1, 0, List(999)), 1.second)
      val result = Await.result(RankingState.updateTimestamps(42, 1, 1, List(1200)), 1.second)
      result._1.get should be equalTo 2
      result._2.get._2 should be equalTo 1
    }

    "correctly compute the number of laps" in new CleanRanking {
      val sites = List(0, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3, 4, 5, 6, 0, 1, 2)
      Await.ready(Future.sequence(sites.zipWithIndex.groupBy(_._1).mapValues(_.map(_._2.toLong)).map { case (siteId, timestamps) =>
        RankingState.updateTimestamps(42, 1, siteId, timestamps)
      }), 1.second)
      val result = Await.result(RankingState.pointsAndRank(42, 1), 1.second).get
      result._1.map(_.lap) should be equalTo Seq(1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3)
    }

    "correctly compute the number of laps with partial data" in new CleanRanking {
      val sites = List(0, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3, 4, 5, 6, 0, 1, 2, 2)
      Await.ready(Future.sequence(sites.zipWithIndex.groupBy(_._1).mapValues(_.map(_._2.toLong)).map { case (siteId, timestamps) =>
        RankingState.updateTimestamps(42, 1, siteId, timestamps)
      }), 1.second)
      val result = Await.result(RankingState.pointsAndRank(42, 1), 1.second).get
      result._1.map(_.lap) should be equalTo Seq(1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 4)
    }
  }

  "pointsAndRank()" should {

    "acknowledge the absence of information about a contestant" in new CleanRanking {
      Await.result(RankingState.pointsAndRank(42, 1), 1.second) should beNone
    }
  }

  "ranks()" should {

    "return the ranks in all races" in new CleanRanking {
      Await.ready(RankingState.updateTimestamps(44, 1, 0, List(1050, 1350)), 1.second)
      Await.ready(RankingState.updateTimestamps(44, 1, 1, List(1250, 1450)), 1.second)
      Await.ready(RankingState.updateTimestamps(42, 1, 0, List(1000, 1300)), 1.second)
      Await.ready(RankingState.updateTimestamps(42, 1, 1, List(1200, 1400)), 1.second)
      Await.ready(RankingState.updateTimestamps(55, 2, 1, List(1400, 1600)), 1.second)
      Await.ready(RankingState.updateTimestamps(55, 2, 0, List(1300, 1500)), 1.second)
      val result = Await.result(RankingState.ranks(), 1.second)
      result must be equalTo Map(1 -> Seq(42, 44), 2 -> Seq(55))
    }
  }

  "raceData()" should {

    "return the full checkpoints" in new CleanRanking {
      Await.ready(RankingState.updateTimestamps(42, 1, 0, List(1000, 1300)), 1.second)
      Await.ready(RankingState.updateTimestamps(42, 1, 1, List(1200, 1400)), 1.second)
      Await.ready(RankingState.updateTimestamps(55, 2, 1, List(1400, 1600)), 1.second)
      Await.ready(RankingState.updateTimestamps(55, 2, 0, List(1300, 1500)), 1.second)
      val result = Await.result(RankingState.raceData(), 1.second)
      result must be equalTo Map((42, 1) -> Seq(Point(0, 1000, 1), Point(1, 1200, 1), Point(0, 1300, 2), Point(1, 1400, 2)),
        (55, 2) -> Seq(Point(0, 1300, 1), Point(1, 1400, 1), Point(0, 1500, 2), Point(1, 1600, 2)))
    }
  }

  "a full simulation" should {

    import Global.dispatcher

    "load a full race information in a reasonable time" in {
      val loader = Future.sequence({
        for (line <- Source.fromInputStream(classOf[ClassLoader].getResourceAsStream("/dummy-timings.txt"), "utf-8").getLines.toList) yield {
          val json = Json.parse(line)
          val contestantId = (json \ "bib").as[Int]
          val raceId = (json \ "race_id").as[Int]
          val siteId = (json \ "site_id").as[Int]
          val timestamps = (json \ "times").as[List[Long]].take(if (raceId == 3) 1 else 3)
          if (raceId > 0)
            RankingState.updateTimestamps(contestantId, raceId, siteId, timestamps).map(_ => NotUsed)
          else
            FastFuture.successful(NotUsed)
        }
      })
      Await.ready(loader, 5.seconds)
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

