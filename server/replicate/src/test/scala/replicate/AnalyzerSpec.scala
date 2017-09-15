package replicate

import org.specs2.execute.Result
import org.specs2.matcher.ResultMatchers
import org.specs2.mutable._
import play.api.libs.json.Json
import replicate.scrutineer.Analyzer
import replicate.scrutineer.Analyzer.{ ArtificialPoint, DeletedPoint, GenuinePoint, KeepPoint, MissingPoint, RemovePoint }
import replicate.state.{ CheckpointsState, PingState }
import replicate.utils.Types._
import replicate.utils.{ Global, Infos }

import scala.concurrent.Await
import scala.concurrent.duration._
import scalaz.@@
import scalaz.Scalaz._

class AnalyzerSpec extends Specification with ResultMatchers {

  sequential

  private val infos: Infos = Json.parse(classOf[ClassLoader].getResourceAsStream("/infos.json")).as[Infos]
  Global.infos = Some(infos)
  (0 to 6).map(SiteId[Int]).foreach(PingState.setLastPing(_, System.currentTimeMillis()))
  RaceUtils.installFullRace(pristine = false)

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
      val points = Await.result(CheckpointsState.timesFor(RaceId(1), ContestantId(688)), 1.second)
      points.size must be equalTo 21
      val result = Analyzer.analyze(RaceId(1), ContestantId(688), points)
      result.checkpoints.size must be equalTo 22
      result.checkpoints.count(_.isInstanceOf[GenuinePoint]) must be equalTo 20
      result.checkpoints.count(_.isInstanceOf[MissingPoint]) must be equalTo 1
      result.checkpoints.count(_.isInstanceOf[RemovePoint]) must be equalTo 1
      infos.checkpoints(result.checkpoints.filter(_.isInstanceOf[MissingPoint]).head.point.siteId).name must be equalTo "La salle des sports, boucle 1"
    }

    "fix a suspicious checkpoint (contestant 24)" in {
      // In race 24, checkpoint "La salle des sports, boucle 1" on lap 3 is obviously late and results in
      // an anomaly in the following segment (speed is 49.62km/h).
      val points = Await.result(CheckpointsState.timesFor(RaceId(1), ContestantId(24)), 1.second)
      points.size must be equalTo 21
      val result = Analyzer.analyze(RaceId(1), ContestantId(24), points)
      result.checkpoints.size must be equalTo 22
      result.checkpoints.count(_.isInstanceOf[GenuinePoint]) must be equalTo 20
      result.checkpoints.count(_.isInstanceOf[MissingPoint]) must be equalTo 1
      result.checkpoints.count(_.isInstanceOf[RemovePoint]) must be equalTo 1
      infos.checkpoints(result.checkpoints.filter(_.isInstanceOf[MissingPoint]).head.point.siteId).name must be equalTo "La salle des sports, boucle 1"
    }

    "let a consistent race untouched (contestant 201)" in {
      val data = Await.result(CheckpointsState.checkpointDataFor(RaceId(1), ContestantId(201)), 1.second)
      data.size must be equalTo 7
      val result = Analyzer.analyze(data)
      result.checkpoints.size must be equalTo 21
      result.checkpoints.count(_.isInstanceOf[GenuinePoint]) must be equalTo 21
    }

    "never suggest a change in a consistent race (contestant 201)" in {
      val points = Await.result(CheckpointsState.timesFor(RaceId(1), ContestantId(201)), 1.second)
      points.size must be equalTo 21
      for (partial ← 1 to points.size) {
        val result = Analyzer.analyze(RaceId(1), ContestantId(201), points.take(partial))
        result.checkpoints.size must be equalTo partial
        result.checkpoints.count(_.isInstanceOf[GenuinePoint]) must be equalTo partial
      }
      success
    }

    "properly reflect the time manipulations" in {
      val data = Await.result(CheckpointsState.checkpointDataFor(RaceId(1), ContestantId(59)), 1.second)
      data.size must be equalTo 7
      val result = Analyzer.analyze(data)
      result.checkpoints.size must be equalTo 22
      result.checkpoints.count(_.isInstanceOf[KeepPoint]) must be equalTo 21
      result.checkpoints.count(_.isInstanceOf[GenuinePoint]) must be equalTo 19
      result.checkpoints.count(_.isInstanceOf[ArtificialPoint]) must be equalTo 2
      result.checkpoints.count(_.isInstanceOf[DeletedPoint]) must be equalTo 1
    }

    "never suggest a change that would later be reverted" in {

      skipped("Test does not give consistent results yet")

      def check(raceId: Int @@ RaceId, contestantId: Int @@ ContestantId): Result = {
        val points = Await.result(CheckpointsState.timesFor(raceId, contestantId), 1.second)
        val analysis = Analyzer.analyze(raceId, contestantId, points)
        val deleted = analysis.checkpoints.collect { case r: RemovePoint ⇒ r.point }
        val inserted = analysis.checkpoints.collect { case m: MissingPoint ⇒ m.point }
        for (partial ← 1 until points.size) {
          val partialAnalysis = Analyzer.analyze(raceId, contestantId, points.take(partial))
          val partialDeleted = partialAnalysis.checkpoints.collect { case r: RemovePoint ⇒ r.point }
          val partialInserted = partialAnalysis.checkpoints.collect { case m: MissingPoint ⇒ m.point }
          for (d ← partialDeleted)
            s"point $d (at step $partial) for bib $contestantId in race $raceId is kept deleted until the end ($partialAnalysis)" <==> (deleted must contain(d))
          for (i ← partialInserted)
            s"point $i (at step $partial) for bib $contestantId in race $raceId is kept inserted until the end ($partialAnalysis)" <==> (inserted must contain(i))
        }
        success
      }

      forall((1 |-> 3).map(RaceId[Int])) { raceId ⇒
        forall(ContestantId.subst(ContestantId.unsubst(Await.result(CheckpointsState.contestants(raceId), 1.second)).toVector.sorted)) { contestantId ⇒
          check(raceId, contestantId)
        }
      }
    }

    "always have a confident latest checkpoint" in {

      skipped("Test does not give consistent results yet")

      def check(raceId: Int @@ RaceId, contestantId: Int @@ ContestantId): Result = {
        val points = Await.result(CheckpointsState.timesFor(raceId, contestantId), 1.second)
        val analysis = Analyzer.analyze(raceId, contestantId, points)
        val finalPoints = analysis.checkpoints.collect { case r: KeepPoint ⇒ r.point }
        var signalledLap = Lap(0)
        var signalledSiteId = SiteId(-1)
        var signalledTime = 0L
        for (partial ← 1 until points.size) {
          val partialAnalysis = Analyzer.analyze(raceId, contestantId, points.take(partial))
          partialAnalysis.checkpoints.reverse.collectFirst { case r: KeepPoint ⇒ r } match {
            case Some(p) if p.point.timestamp > signalledTime && (Lap.unwrap(p.lap) > Lap.unwrap(signalledLap) || (p.lap == signalledLap && SiteId.unwrap(p.point.siteId) > SiteId.unwrap(signalledSiteId))) ⇒
              s"point $p (at step $partial) for bib $contestantId in race $raceId is kept valid until the end ($analysis)" <==> (finalPoints must contain(p.point))
              signalledLap = p.lap
              signalledSiteId = p.point.siteId
              signalledTime = p.point.timestamp
            case _ ⇒
            // Either there is no point to be kept or we have signalled it already
          }
        }
        success
      }

      forall((1 |-> 3).map(RaceId[Int])) { raceId: Int @@ RaceId ⇒
        forall(ContestantId.subst(ContestantId.unsubst(Await.result(CheckpointsState.contestants(raceId), 1.second)).toVector.sorted)) { contestantId: Int @@ ContestantId ⇒
          check(raceId, contestantId)
        }
      }
    }

    "be able to use consistent race duration" in {
      infos.races(RaceId(1)).endTime - infos.races(RaceId(1)).startTime must be equalTo 24.hours.toMillis
      infos.races(RaceId(2)).endTime - infos.races(RaceId(2)).startTime must be equalTo 13.hours.toMillis
      infos.races(RaceId(3)).endTime - infos.races(RaceId(3)).startTime must be equalTo 24.hours.toMillis
      infos.races(RaceId(5)).endTime - infos.races(RaceId(5)).startTime must be equalTo 13.hours.toMillis
    }

  }

}
