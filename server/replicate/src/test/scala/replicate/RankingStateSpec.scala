package replicate

import akka.stream.scaladsl.{ Sink, Source }
import org.specs2.mutable._
import play.api.libs.json.Json
import replicate.scrutineer.Analyzer
import replicate.state.{ CheckpointsState, PingState, RankingState }
import replicate.utils.Types._
import replicate.utils.{ Global, Infos }

import scala.concurrent.Await
import scala.concurrent.duration._
import scalaz.@@

class RankingStateSpec extends Specification {

  import Global.{ dispatcher, flowMaterializer }

  sequential

  "RankingState" should {

    val infos: Infos = Json.parse(classOf[ClassLoader].getResourceAsStream("/infos.json")).as[Infos]
    Global.infos = Some(infos)
    (0 to 6).map(SiteId[Int]).foreach(PingState.setLastPing(_, System.currentTimeMillis()))
    RaceUtils.installFullRace(pristine = true)

    def first10(raceId: Int @@ RaceId): Vector[Int @@ ContestantId] = {
      val inserts = Source.fromFuture(CheckpointsState.contestants(raceId))
        .flatMapConcat(Source(_))
        .mapAsync(4)(contestantId ⇒ CheckpointsState.timesFor(raceId, contestantId).map(Analyzer.analyze(raceId, contestantId, _)))
        .mapAsync(16)(RankingState.enterAnalysis)
        .runWith(Sink.ignore)
      Await.ready(inserts, 5.seconds)
      val race = Await.result(RankingState.rankingsFor(raceId), 1.second)
      race.take(10).map(_.contestantId)
    }

    "get the right final order in race 1" in {
      ContestantId.unsubst(first10(RaceId(1))) must be equalTo Vector(688, 24, 201, 719, 522, 242, 241, 110, 314, 217)
    }

    "get the right final order in race 2" in {
      ContestantId.unsubst(first10(RaceId(2))) must be equalTo Vector(393, 491, 56, 490, 71, 91, 287, 670, 933, 560)
    }

    "get the right final order in race 3" in {
      ContestantId.unsubst(first10(RaceId(3))) must be equalTo Vector(879, 867, 738, 498, 280, 735, 788, 790, 701, 759)
    }
  }

}
