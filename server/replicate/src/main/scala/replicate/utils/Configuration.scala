package replicate.utils

import play.api.libs.json.{Json, Reads}

case class Configuration(dbname: String, tests_allowed: Boolean)

object Configuration {
  implicit val configurationReads: Reads[Configuration] = Json.reads[Configuration]
}
