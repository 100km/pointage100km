package replicate.alerts

import net.rfc1149.canape.Database
import play.api.libs.json.Json
import replicate.utils.Global
import spray.http.HttpCharsets

import scala.concurrent.{ExecutionContext, Future}

object Ranking {

  def headOfRace(raceId: Int, database: Database)(implicit ec: ExecutionContext): Future[Option[Int]] = {
    Global.infos.flatMap(_.races.get(raceId)).fold(Future.successful(None: Option[Int])) { raceInfo =>
      val response = database.list("main_display", "global-ranking", "global-ranking",
        Seq("limit" -> "1",
          "startkey" -> Json.stringify(Json.arr(raceId, -raceInfo.laps)),
          "endkey" -> Json.stringify(Json.arr(raceId + 1))))
      response.filter(_.status.isSuccess).map { r =>
        val result = Json.parse(r.entity.asString(HttpCharsets.`UTF-8`))
        (result \\ "id").headOption.map { id =>
          // id is of the form checkpoints-CHECKPOINT-CONTESTANT
          id.as[String].split('-').last.toInt
        }
      }
    }
  }

}
