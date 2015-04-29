package replicate.messaging

import scala.concurrent.Future
import scalaj.http.{Http, HttpOptions}

class FreeMobileSMS(override val officerId: String, user: String, password: String) extends Messaging {

  override val serviceName = "FreeMobileSMS"

  override def sendMessage(message: Message): Future[Option[String]] = {
    Future {
      val response = Http("https://smsapi.free-mobile.fr/sendmsg").option(HttpOptions.allowUnsafeSSL)
        .param("user", user).param("pass", password).param("msg", message.toString).asString
      if (response.isSuccess)
      // This backend does not support alteration of previously sent messages
        None
      else
        sys.error(s"""unable to send message "$toString" to $this""")
    }
  }

}
