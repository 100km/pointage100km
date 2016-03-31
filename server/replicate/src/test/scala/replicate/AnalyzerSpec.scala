package replicate

import org.specs2.mutable._
import play.api.libs.json.Json
import replicate.scrutineer.Analyzer
import replicate.scrutineer.Analyzer.{CorrectPoint, MissingPoint, RemovePoint}
import replicate.state.{CheckpointsState, PingState}
import replicate.utils.{Global, Infos}

import scala.concurrent.Await
import scala.concurrent.duration._

class AnalyzerSpec extends Specification {

  sequential

  private val infos: Infos = Json.parse(classOf[ClassLoader].getResourceAsStream("/infos.json")).as[Infos]
  Global.infos = Some(infos)
  (0 to 6).foreach(PingState.setLastPing(_, System.currentTimeMillis()))
  CheckpointsStateSpec.installFullRace()

  "speedBetween" should {

    "return the correct value" in {
      Analyzer.speedBetween(15, 20, 123456, 123456 + 3600 * 1000) must be equalTo 5
    }
  }

  "median" should {

    "return the correct value for a one element list" in {
      Analyzer.median(List(10)) should be equalTo 10
    }

    "return the correct value for a two element list" in {
      Analyzer.median(List(20, 10)) should be equalTo 15
    }

    "return the correct value for a list with an odd number of elements" in {
      Analyzer.median(List(10, 22, 30)) should be equalTo 22
    }

    "return the correct value for a list with an even number of elements" in {
      Analyzer.median(List(10, 20, 30, 40)) should be equalTo 25
    }

  }

  "analyze" should {

    "fix a suspicious checkpoint (contestant 688)" in {
      // In race 688, checkpoint "La salle des sports, boucle 1" on lap 2 is obviously late and results in
      // an anomaly in the following segment (speed is 22.89km/h).
      val points = Await.result(CheckpointsState.timesFor(1, 688), 1.second)
      points.size must be equalTo 21
      val result = Analyzer.analyze(1, 688, points)
      result.checkpoints.size must be equalTo 22
      result.checkpoints.count(_.isInstanceOf[CorrectPoint]) must be equalTo 20
      result.checkpoints.count(_.isInstanceOf[MissingPoint]) must be equalTo 1
      result.checkpoints.count(_.isInstanceOf[RemovePoint]) must be equalTo 1
      infos.checkpoints(result.checkpoints.filter(_.isInstanceOf[MissingPoint]).head.point.siteId).name must be equalTo "La salle des sports, boucle 1"
    }

    "fix a suspicious checkpoint (contestant 24)" in {
      // In race 24, checkpoint "La salle des sports, boucle 1" on lap 3 is obviously late and results in
      // an anomaly in the following segment (speed is 49.62km/h).
      val points = Await.result(CheckpointsState.timesFor(1, 24), 1.second)
      points.size must be equalTo 21
      val result = Analyzer.analyze(1, 24, points)
      result.checkpoints.size must be equalTo 22
      result.checkpoints.count(_.isInstanceOf[CorrectPoint]) must be equalTo 20
      result.checkpoints.count(_.isInstanceOf[MissingPoint]) must be equalTo 1
      result.checkpoints.count(_.isInstanceOf[RemovePoint]) must be equalTo 1
      infos.checkpoints(result.checkpoints.filter(_.isInstanceOf[MissingPoint]).head.point.siteId).name must be equalTo "La salle des sports, boucle 1"
    }

    "let a consistent race untouched (contestant 201)" in {
      val points = Await.result(CheckpointsState.timesFor(1, 201), 1.second)
      points.size must be equalTo 21
      val result = Analyzer.analyze(1, 201, points)
      result.checkpoints.size must be equalTo 21
      result.checkpoints.count(_.isInstanceOf[CorrectPoint]) must be equalTo 21
    }

    "never suggest a change in a consistent race (contestant 201)" in {
      val points = Await.result(CheckpointsState.timesFor(1, 201), 1.second)
      points.size must be equalTo 21
      for (partial ← 1 to points.size) {
        val result = Analyzer.analyze(1, 201, points.take(partial))
        result.checkpoints.size must be equalTo partial
        result.checkpoints.count(_.isInstanceOf[CorrectPoint]) must be equalTo partial
      }
      success
    }

  }

}
