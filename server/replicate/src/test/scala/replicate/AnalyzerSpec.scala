package replicate

import org.specs2.mutable._
import play.api.libs.json.Json
import replicate.RankingStateSpec.CheckpointEntry
import replicate.scrutineer.Analyzer
import replicate.scrutineer.models.CheckpointStatus.{Missing, Ok}
import replicate.state.{PingState, RankingState}
import replicate.utils.{Global, Infos}

import scala.concurrent.Await
import scala.concurrent.duration._

class AnalyzerSpec extends Specification {

  sequential

  private val infos: Infos = Json.parse(classOf[ClassLoader].getResourceAsStream("/infos.json")).as[Infos]
  private val raceData: Seq[CheckpointEntry] = RankingStateSpec.loadRaceData.toSeq
  Global.infos = Some(infos)
  (0 to 6).foreach(PingState.setLastPing(_, System.currentTimeMillis()))

  "speedBetween" should {

    "return the correct value" in {
      Analyzer.speedBetween(15, 20, 123456, 123456 + 3600 * 1000) must be equalTo 5
    }
  }

  "analyze" should {

    "fix a suspicious checkpoint (contestant 688)" in {
      // In race 688, checkpoint "La salle des sports, boucle 1" on lap 2 is obviously late and results in
      // an anomaly in the following segment (speed is 22.89km/h).
      RankingStateSpec.installSoleContestant(688, raceData)
      val rankingInfo = Await.result(RankingState.pointsAndRank(688, 1), 1.second)
      rankingInfo.points.size must be equalTo 21
      val result = Analyzer.analyze(688, 1, rankingInfo)
      result.checkpoints.size must be equalTo 22
      result.checkpoints.count(_.status.isInstanceOf[Ok]) must be equalTo 20
      result.checkpoints.count(_.status == Missing) must be equalTo 1
      infos.checkpoints(result.checkpoints.filter(_.status == Missing).head.siteId).name must be equalTo "La salle des sports, boucle 1"
    }

    "fix a suspicious checkpoint (contestant 24)" in {
      // In race 24, checkpoint "La salle des sports, boucle 1" on lap 3 is obviously late and results in
      // an anomaly in the following segment (speed is 49.62km/h).
      RankingStateSpec.installSoleContestant(24, raceData)
      val rankingInfo = Await.result(RankingState.pointsAndRank(24, 1), 1.second)
      rankingInfo.points.size must be equalTo 21
      val result = Analyzer.analyze(24, 1, rankingInfo)
      result.checkpoints.size must be equalTo 22
      result.checkpoints.count(_.status.isInstanceOf[Ok]) must be equalTo 20
      result.checkpoints.count(_.status == Missing) must be equalTo 1
      infos.checkpoints(result.checkpoints.filter(_.status == Missing).head.siteId).name must be equalTo "La salle des sports, boucle 1"
    }

    "let a consistent race untouched (contestant 201)" in {
      RankingStateSpec.installSoleContestant(201, raceData)
      val rankingInfo = Await.result(RankingState.pointsAndRank(201, 1), 1.second)
      rankingInfo.points.size must be equalTo 21
      val result = Analyzer.analyze(201, 1, rankingInfo)
      result.checkpoints.size must be equalTo 21
      result.checkpoints.count(_.status.isInstanceOf[Ok]) must be equalTo 21
    }

    "never suggest a change in a consistent race (contestant 201)" in {
      RankingStateSpec.installSoleContestant(201, raceData)
      val rankingInfo = Await.result(RankingState.pointsAndRank(201, 1), 1.second)
      rankingInfo.points.size must be equalTo 21
      for (partial <- 1 to rankingInfo.points.size) {
        val result = Analyzer.analyze(201, 1, rankingInfo.copy(points = rankingInfo.points.take(partial)))
        result.checkpoints.size must be equalTo partial
        result.checkpoints.count(_.status.isInstanceOf[Ok]) must be equalTo partial
      }
      success
    }

  }

}
