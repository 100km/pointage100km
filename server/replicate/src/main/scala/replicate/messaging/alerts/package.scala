package replicate.messaging

import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

package object alerts {

  def startFromConfig(officerId: String, config: Config)(implicit context: ActorRefFactory): ActorRef = {
    val service = config.as[String]("type")
    val props = service match {
      case "freemobile-sms" ⇒ Props(new FreeMobileSMS(config.as[String]("user"), config.as[String]("password")))
      case "pushbullet"     ⇒ Props(new Pushbullet(config.as[String]("token")))
      case "system"         ⇒ Props(new SystemLogger)
      case "telegram"       ⇒ Props(new Telegram(config.as[String]("id")))
      case s                ⇒ sys.error(s"Unknown officer type $s for officer $officerId")
    }
    context.actorOf(props, if (service == officerId) service else s"$service-$officerId")
  }

}
