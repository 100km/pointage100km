package replicate.maintenance

import akka.Done
import akka.event.Logging
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorAttributes, Materializer, Supervision}
import net.rfc1149.canape._
import play.api.libs.json.JsObject
import replicate.utils.{Global, LoggingError}

import scala.concurrent.Future
import scala.language.postfixOps

object PingService extends LoggingError {

  override val log = Logging(Global.system, "pingService")

  def pingService(siteId: Int, db: Database)(implicit fm: Materializer): Sink[JsObject, Future[Done]] = {
    val prefix = s"checkpoints-$siteId-"
    Flow[JsObject]
      .filter(js ⇒ (js \ "id").as[String].startsWith(prefix)).map(_ ⇒ false)
      .keepAlive(Global.pingTimeout, () ⇒ true)
      .filter(identity).prepend(Source.single(true))
      .mapAsync(1)(_ ⇒ withError(steenwerck.ping(db, siteId), "cannot ping database"))
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      .toMat(Sink.ignore)(Keep.right)
  }

  def launchPingService(siteId: Int, db: Database)(implicit fm: Materializer): Future[Done] = {
    db.changesSource(Map("filter" → "bib_input/no-ping")).toMat(pingService(siteId, db))(Keep.right).run()
  }

}
