package net.rfc1149.rxtelegram

import com.typesafe.config.{ Config, ConfigFactory }
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration.FiniteDuration

class Options(config: Config = ConfigFactory.load()) {
  val rxtelegramConfig: Config = config.as[Config]("rxtelegram")
  val httpMinErrorRetryDelay: FiniteDuration = rxtelegramConfig.as[FiniteDuration]("http-min-error-retry-delay")
  val httpMaxErrorRetryDelay: FiniteDuration = rxtelegramConfig.as[FiniteDuration]("http-max-error-retry-delay")
  val longPollingDelay: FiniteDuration = rxtelegramConfig.as[FiniteDuration]("long-polling-delay")
  val updatesBatchSize: Int = rxtelegramConfig.as[Int]("updates-batch-size")
}

