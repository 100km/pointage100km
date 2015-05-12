package replicate.messaging

import akka.actor.Actor

import scalaj.http.{Http, HttpOptions}

class FreeMobileSMS(user: String, password: String) extends Actor with Messaging {

  override def sendMessage(message: Message): Option[String] = {
    val response = Http("https://smsapi.free-mobile.fr/sendmsg").option(HttpOptions.allowUnsafeSSL)
      .param("user", user).param("pass", password).param("msg", message.toString).asString
    if (response.isSuccess)
    // This backend does not support alteration of previously sent messages
      None
    else
      sys.error(response.statusLine)
  }

}