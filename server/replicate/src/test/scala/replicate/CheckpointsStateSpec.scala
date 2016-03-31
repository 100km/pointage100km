package replicate

import org.specs2.mutable._
import org.specs2.specification.Scope
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads}
import replicate.state.CheckpointsState
import replicate.state.CheckpointsState.{CheckpointData, Point}
import replicate.utils.{Global, Infos}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source

class CheckpointsStateSpec extends Specification {

  import CheckpointsStateSpec._

  sequential

  trait CleanRanking extends Scope with BeforeAfter {
    implicit val dispatcher = Global.dispatcher
    def before() = Await.ready(CheckpointsState.reset(), 1.second)
    def after() = Await.ready(CheckpointsState.reset(), 1.second)
  }

  "setTimes()" should {

    "return a sorted list of timestamps" in new CleanRanking {
      val result1 = Await.result(CheckpointsState.setTimes(CheckpointData(1, 42, 1, Seq(950, 1300))), 1.second)
      result1 must be equalTo Seq(Point(1, 950), Point(1, 1300))
      val result2 = Await.result(CheckpointsState.setTimes(CheckpointData(1, 42, 2, Seq(800, 900, 1000, 1100))), 1.second)
      result2 must be equalTo Seq(Point(2, 800), Point(2, 900), Point(1, 950), Point(2, 1000), Point(2, 1100), Point(1, 1300))
    }
  }

  "timesFor()" should {

    "acknowledge the absence of information about a contestant" in new CleanRanking {
      Await.result(CheckpointsState.timesFor(1, 42), 1.second) must beEmpty
    }

    "return the existing points for a contestant" in new CleanRanking {
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 42, 1, Seq(950))), 1.second)
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 42, 2, Seq(1000, 900, 1100, 800))), 1.second)
      Await.result(CheckpointsState.timesFor(1, 42), 1.second) must be equalTo Seq(Point(2, 800), Point(2, 900), Point(1, 950), Point(2, 1000), Point(2, 1100))
    }
  }

  "contestants()" should {

    "return the full list of contestants" in new CleanRanking {
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 42, 1, Seq(950))), 1.second)
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 50, 2, Seq(1000, 900, 1100, 800))), 1.second)
      Await.result(CheckpointsState.contestants(1), 1.second) must be equalTo Set(42, 50)
    }

    "remove contestants with no points" in new CleanRanking {
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 42, 1, Seq(950))), 1.second)
      Await.ready(CheckpointsState.setTimes(CheckpointData(1, 42, 1, Seq())), 1.second)
      Await.result(CheckpointsState.contestants(1), 1.second) must beEmpty
    }
  }

  "a full simulation" should {

    "load a full race information in a reasonable time" in {
      Await.result(installFullRace(), 5.seconds) must be equalTo 5193
    }

  }

}

object CheckpointsStateSpec {

  implicit val dispatcher = Global.dispatcher

  case class CheckpointEntry(raceId: Int, contestantId: Int, siteId: Int, times: Seq[Long],
      deletedTimes: Option[Seq[Long]], artificialTimes: Option[Seq[Long]]) {
    def checkpointData = CheckpointData(raceId, contestantId, siteId, times)
  }

  implicit val checkpointEntryReads: Reads[CheckpointEntry] = (
    (JsPath \ "race_id").read[Int] and
    (JsPath \ "bib").read[Int] and
    (JsPath \ "site_id").read[Int] and
    (JsPath \ "times").read[Seq[Long]] and
    (JsPath \ "deleted_times").readNullable[Seq[Long]] and
    (JsPath \ "artificial_times").readNullable[Seq[Long]]
  )(CheckpointEntry.apply _)

  def loadRaceData: Iterator[CheckpointEntry] =
    Source.fromInputStream(classOf[ClassLoader].getResourceAsStream("/dummy-timings.txt"), "utf-8").getLines.map(Json.parse(_).as[CheckpointEntry])

  def loadInfos: Infos = Json.parse(classOf[ClassLoader].getResourceAsStream("/infos.json")).as[Infos]

  def installFullRace(): Future[Int] = {
    val infos = loadInfos
    CheckpointsState.reset()
    Future.sequence(for (entry ← loadRaceData if entry.raceId > 0) yield CheckpointsState.setTimes(entry.checkpointData)).map(_.size)
  }

}
