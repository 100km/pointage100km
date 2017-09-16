package replicate.messaging.sms

import akka.actor.{Actor, ActorLogging}

class FakeSMS extends Actor with ActorLogging {

  override def preStart = log.info("fake SMS service starting")

  def receive = {
    case (recipient: String, message: String) ⇒
      log.info("sending fake SMS to {}: {}", recipient, message)
  }

}
