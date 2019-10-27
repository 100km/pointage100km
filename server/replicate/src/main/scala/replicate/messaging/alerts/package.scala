package replicate.messaging

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

package object alerts {

  def startFromConfig(context: ActorContext[_], officerId: String, config: Config): ActorRef[Messaging.Protocol] = {
    val service = config.as[String]("type")
    val behavior: Behavior[Messaging.Protocol] = service match {
      case "freemobile-sms" => new FreeMobileSMS(config.as[String]("user"), config.as[String]("password"))
      case "pushbullet"     => new Pushbullet(config.as[String]("token"))
      case "system"         => new SystemLogger
      case "telegram"       => new Telegram(config.as[String]("id"))
      case s                => sys.error(s"Unknown officer type $s for officer $officerId")
    }
    context.spawn(behavior, if (service == officerId) service else s"$service-$officerId")
  }

}
