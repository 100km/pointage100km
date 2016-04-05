package replicate

import play.api.libs.json.Json
import replicate.state.CheckpointsState
import replicate.state.CheckpointsState.CheckpointData
import replicate.utils.{Global, Infos}

import scala.concurrent.Future
import scala.io.Source

object RaceUtils {

  implicit val dispatcher = Global.dispatcher

  def loadRaceData: Iterator[CheckpointData] =
    Source.fromInputStream(classOf[ClassLoader].getResourceAsStream("/dummy-timings.txt"), "utf-8").getLines.map(Json.parse(_).as[CheckpointData])

  def loadInfos: Infos = Json.parse(classOf[ClassLoader].getResourceAsStream("/infos.json")).as[Infos]

  def installFullRace(pristine: Boolean = false): Future[Int] = {
    val infos = loadInfos
    CheckpointsState.reset()
    Future.sequence(for (checkpointData ← loadRaceData if checkpointData.raceId > 0) yield {
      CheckpointsState.setTimes(if (pristine) checkpointData.pristine else checkpointData)
    }).map(_.size)
  }

}