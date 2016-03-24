package replicate

import org.specs2.mutable._
import org.specs2.specification.Scope
import replicate.state.RankingState
import replicate.state.RankingState.RaceRanking
import replicate.utils.Global

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

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

}
