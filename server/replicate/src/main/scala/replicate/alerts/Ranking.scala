package replicate.alerts

import akka.stream.FlowMaterializer
import net.rfc1149.canape.{Couch, Database}
import play.api.libs.json.{JsValue, Json}
import replicate.utils.Infos.RaceInfo

import scala.concurrent.{ExecutionContext, Future}

object Ranking {

  def headOfRace(raceInfo: RaceInfo, database: Database)(implicit fm: FlowMaterializer, ec: ExecutionContext): Future[Seq[Int]] = {
    val response = database.list("main_display", "global-ranking", "global-ranking",
      Seq("startkey" -> Json.stringify(Json.arr(raceInfo.raceId, -raceInfo.laps)), "endkey" -> Json.stringify(Json.arr(raceInfo.raceId + 1))))
    response.filter(_.status.isSuccess()).flatMap(r => Couch.jsonUnmarshaller[JsValue]().apply(r.entity)).map { result =>
      (result \ "rows").as[Array[JsValue]].headOption.map(_ \ "contestants" \\ "id" map { id =>
        // id is of the form checkpoints-CHECKPOINT-CONTESTANT
        id.as[String].split('-').last.toInt
      }).getOrElse(Seq())
    }
  }

}
