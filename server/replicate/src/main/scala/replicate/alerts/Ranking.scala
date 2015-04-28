package replicate.alerts

import akka.stream.FlowMaterializer
import net.rfc1149.canape.{Couch, Database}
import play.api.libs.json.{JsValue, Json}
import replicate.utils.Global

import scala.concurrent.{ExecutionContext, Future}

object Ranking {

  def headOfRace(raceId: Int, database: Database)(implicit fm: FlowMaterializer, ec: ExecutionContext): Future[Option[Int]] = {
    Global.infos.flatMap(_.races.get(raceId)).fold(Future.successful(None: Option[Int])) { raceInfo =>
      val response = database.list("main_display", "global-ranking", "global-ranking",
        Seq("startkey" -> Json.stringify(Json.arr(raceId)), "endkey" -> Json.stringify(Json.arr(raceId + 1))))
      response.filter(_.status.isSuccess).flatMap(r => Couch.jsonUnmarshaller[JsValue]().apply(r.entity)).map { result =>
        (result \\ "id").headOption.map { id =>
          // id is of the form checkpoints-CHECKPOINT-CONTESTANT
          id.as[String].split('-').last.toInt
        }
      }
    }
  }

}
