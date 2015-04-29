package replicate.utils

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.{Json, Reads}

case class Configuration(dbname: String, tests_allowed: Boolean) {

  lazy val adminLink: Uri =
    Uri().withScheme("http")
      .withHost(steenwerck.config.as[String]("steenwerck.master.host")).withPort(steenwerck.config.as[Int]("steenwerck.master.port"))
      .withPath(Path("") / dbname / "_design" / "admin" / "admin.html")
}

object Configuration {
  implicit val configurationReads: Reads[Configuration] = Json.reads[Configuration]
}
