package replicate.messaging

import scala.concurrent.Future
import scalaj.http.{Http, HttpOptions}

class FreeMobileSMS(override val officerId: String, user: String, password: String) extends Messaging {

  override val serviceName = "FreeMobileSMS"

  override def sendMessage(title: String, body: String, url: Option[String] = None): Future[Option[String]] = {
    val message = s"[$title] $body" + url.fold("")(l => s" ($l)")
    Future {
      val response = Http("https://smsapi.free-mobile.fr/sendmsg").option(HttpOptions.allowUnsafeSSL)
        .param("user", user).param("pass", password).param("msg", message).asString
      if (response.isSuccess)
      // This backend does not support alteration of previously sent messages
        None
      else
        sys.error( s"""unable to send message "$message" to $this:""")
    }
  }

}
