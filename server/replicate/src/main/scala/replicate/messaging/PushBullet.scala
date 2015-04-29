package replicate.messaging

import java.io.ByteArrayOutputStream

import play.api.libs.json.Json
import replicate.utils.Global
import sun.misc.BASE64Encoder

import scala.concurrent.Future
import scalaj.http.Http

class PushBullet(override val officerId: String, bearerToken: String) extends Messaging {

  import PushBullet._

  override def sendMessage(message: String): Future[Boolean] = {
    Future {
      val payload = Json.obj("type" -> "note", "title" -> "Steenwerck 100km", "body" -> message, "icon" -> base64icon)
      Global.log.info(s"Sending payload $payload")
      val response = Http("https://api.pushbullet.com/v2/pushes").postData(payload.toString().getBytes("UTF-8"))
                      .header("Content-Type", "application/json")
                      .header("Authorization", s"Bearer $bearerToken")
                      .asBytes
      response.isSuccess
    }
  }
}

object PushBullet {

  val base64icon: String = {
    val in = getClass.getResourceAsStream("/pushbullet-icon.jpg")
    require(in != null, "Resource not found")
    val out = new ByteArrayOutputStream()
    new BASE64Encoder().encode(in, out)
    out.toString("UTF-8")
  }
  Global.log.info(s"Icon: $base64icon")
}
