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
      CheckpointsState.sortedTimestamps(result1) must be equalTo List(Point(1, 950), Point(1, 1300))
      val result2 = Await.result(CheckpointsState.setTimes(CheckpointData(1, 42, 2, List(800, 900, 1000, 1100), Nil, Nil)), 1.second)
      CheckpointsState.sortedTimestamps(result2) must be equalTo List(Point(2, 800), Point(2, 900), Point(1, 950), Point(2, 1000), Point(2, 1100), Point(1, 1300))
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

  "checkpointDataFor()" should {

    "acknowledge the absence of information about a contestant" in new CleanRanking {
      Await.result(CheckpointsState.checkpointDataFor(1, 42), 1.second) must beEmpty
    }

    "return the existing points for a contestant" in new CleanRanking {
      private val cpd1: CheckpointData = CheckpointData(1, 42, 1, List(950), Nil, Nil)
      private val cpd2: CheckpointData = CheckpointData(1, 42, 2, List(1000, 900, 1100, 800), Nil, Nil)
      Await.ready(CheckpointsState.setTimes(cpd1), 1.second)
      Await.ready(CheckpointsState.setTimes(cpd2), 1.second)
      Await.result(CheckpointsState.checkpointDataFor(1, 42), 1.second) must be equalTo List(cpd1, cpd2)
    }

    "replace data for a given site id" in new CleanRanking {
      private val cpd1: CheckpointData = CheckpointData(1, 42, 1, List(950), Nil, Nil)
      private val cpd2: CheckpointData = CheckpointData(1, 42, 2, List(1000, 900, 1100, 800), Nil, Nil)
      private val cpd1bis: CheckpointData = CheckpointData(1, 42, 1, List(950), Nil, Nil)
      Await.ready(CheckpointsState.setTimes(cpd1), 1.second)
      Await.ready(CheckpointsState.setTimes(cpd2), 1.second)
      Await.ready(CheckpointsState.setTimes(cpd1bis), 1.second)
      Await.result(CheckpointsState.checkpointDataFor(1, 42), 1.second) must be equalTo List(cpd2, cpd1bis)
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
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 42, 1, List(), List(950), Nil)), 1.second)
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 50, 2, List(1000, 900, 1100, 800), Nil, Nil)), 1.second)
      Await.result(CheckpointsState.contestants(1), 1.second) must be equalTo Set(50)
    }
  }

  "a full simulation" should {

    "load a full race information in a reasonable time" in {
      Await.result(RaceUtils.installFullRace(), 5.seconds) must be equalTo 5193
    }

    "restore the saved checkpoints" in {
      val result = Await.result(CheckpointsState.checkpointDataFor(1, 59), 1.second)
      result must have size 7
      result.foreach(_.timestamps must have size 3)
      result.map(_.deletedTimestamps.size).sum must be equalTo 1
      result.map(_.insertedTimestamps.size).sum must be equalTo 2
    }

  }

}

