package replicate

import org.specs2.mutable._
import org.specs2.specification.Scope
import replicate.models.CheckpointData
import replicate.state.CheckpointsState
import replicate.state.CheckpointsState.Point
import replicate.utils.Global

import scala.concurrent.Await
import scala.concurrent.duration._

class CheckpointsStateSpec extends Specification {

  sequential

  trait CleanRanking extends Scope with BeforeAfter {
    implicit val dispatcher = Global.dispatcher
    def before() = Await.ready(CheckpointsState.reset(), 1.second)
    def after() = Await.ready(CheckpointsState.reset(), 1.second)
  }

  "checkpointData#pristine()" should {

    "insert deleted timestamps" in {
      CheckpointData(1, 42, 1, List(950, 1300), Nil, Nil).pristine should be equalTo CheckpointData(1, 42, 1, List(950, 1300), Nil, Nil)
      CheckpointData(1, 42, 1, List(950, 1300), List(1000, 1100), Nil).pristine should be equalTo CheckpointData(1, 42, 1, List(950, 1000, 1100, 1300), Nil, Nil)
    }

    "remove artificial timestamps" in {
      CheckpointData(1, 42, 1, List(950, 1000, 1100), Nil, Nil).pristine should be equalTo CheckpointData(1, 42, 1, List(950, 1000, 1100), Nil, Nil)
      CheckpointData(1, 42, 1, List(950, 1000, 1100), Nil, List(950, 1100)).pristine should be equalTo CheckpointData(1, 42, 1, List(1000), Nil, Nil)
    }
  }

  "setTimes()" should {

    "return a sorted list of timestamps" in new CleanRanking {
      val result1 = Await.result(CheckpointsState.setTimes(CheckpointData(1, 42, 1, List(950, 1300), Nil, Nil)), 1.second)
      result1 must be equalTo List(Point(1, 950), Point(1, 1300))
      val result2 = Await.result(CheckpointsState.setTimes(CheckpointData(1, 42, 2, List(800, 900, 1000, 1100), Nil, Nil)), 1.second)
      result2 must be equalTo List(Point(2, 800), Point(2, 900), Point(1, 950), Point(2, 1000), Point(2, 1100), Point(1, 1300))
    }
  }

  "timesFor()" should {

    "acknowledge the absence of information about a contestant" in new CleanRanking {
      Await.result(CheckpointsState.timesFor(1, 42), 1.second) must beEmpty
    }

    "return the existing points for a contestant" in new CleanRanking {
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 42, 1, List(950), Nil, Nil)), 1.second)
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 42, 2, List(1000, 900, 1100, 800), Nil, Nil)), 1.second)
      Await.result(CheckpointsState.timesFor(1, 42), 1.second) must be equalTo List(Point(2, 800), Point(2, 900), Point(1, 950), Point(2, 1000), Point(2, 1100))
    }
  }

  "contestants()" should {

    "return the full list of contestants" in new CleanRanking {
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 42, 1, List(950), Nil, Nil)), 1.second)
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 50, 2, List(1000, 900, 1100, 800), Nil, Nil)), 1.second)
      Await.result(CheckpointsState.contestants(1), 1.second) must be equalTo Set(42, 50)
    }

    "remove contestants with no points" in new CleanRanking {
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 42, 1, List(950), Nil, Nil)), 1.second)
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 42, 1, List(), Nil, Nil)), 1.second)
      Await.result(CheckpointsState.contestants(1), 1.second) must beEmpty
    }
  }

  "a full simulation" should {

    "load a full race information in a reasonable time" in {
      Await.result(RaceUtils.installFullRace(), 5.seconds) must be equalTo 5193
    }

  }

}

