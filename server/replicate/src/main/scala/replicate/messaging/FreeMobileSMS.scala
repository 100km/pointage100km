package replicate.messaging

import scala.concurrent.Future
import scalaj.http.{HttpOptions, Http}

class FreeMobileSMS(override val officerId: String, user: String, password: String) extends Messaging {

  override def sendMessage(message: String): Future[Boolean] =
    Future {
      val response = Http("https://smsapi.free-mobile.fr/sendmsg").option(HttpOptions.allowUnsafeSSL)
        .param("user", user).param("pass", password).param("msg", message).asString
      response.isSuccess
    }

}
