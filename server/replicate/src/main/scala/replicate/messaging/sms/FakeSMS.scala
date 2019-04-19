package replicate.messaging.sms

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object FakeSMS {

  val fakeSMS: Behavior[SMSMessage] = Behaviors.setup { context ⇒
    context.log.info("fake SMS service starting")
    Behaviors.receiveMessagePartial {
      case SMSMessage(recipient, message) ⇒
        context.log.info("sending fake SMS to {}: {}", recipient, message)
        Behaviors.same
    }
  }
}
