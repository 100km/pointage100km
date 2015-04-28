package replicate.messaging

import play.api.libs.json.Json

import scala.concurrent.Future
import scalaj.http.Http

class PushBullet(bearerToken: String) extends Messaging {

  override def sendMessage(message: String): Future[Boolean] = {
    Future {
      val payload = Json.obj("type" -> "note", "title" -> "Steenwerck 100km", "body" -> message)
      val response = Http("https://api.pushbullet.com/v2/pushes").postData(payload.toString().getBytes("UTF-8"))
                      .header("Content-Type", "application/json")
                      .header("Authorization", s"Bearer $bearerToken")
                      .asBytes
      response.isSuccess
    }
  }
}
