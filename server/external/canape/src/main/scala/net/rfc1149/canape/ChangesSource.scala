package net.rfc1149.canape

import akka.Done
import akka.stream.scaladsl.Source
import net.ceedubs.ficus.Ficus._
import net.rfc1149.canape.Couch.StatusError
import net.rfc1149.canape.Database.{FromNow, UpdateSequence}
import play.api.libs.json.{JsError, JsObject, JsSuccess, Json}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object ChangesSource {

  def changesSource(database: Database, params: Map[String, String] = Map(), extraParams: JsObject = Json.obj(),
    sinceSeq: UpdateSequence = FromNow): Source[JsObject, Future[Done]] =
    {
      val reconnectionDelay = database.couch.canapeConfig.as[FiniteDuration]("changes-source.reconnection-delay")
      var currentSinceSeq = sinceSeq

      def connectOnce(): Source[JsObject, Future[Done]] =
        database.continuousChanges(params + ("since" -> currentSinceSeq.toString), extraParams)
          .recover {
            // The only errors we do not want to recover are HTTP connection errors
            case t if !t.isInstanceOf[StatusError] =>
              Json.obj()
          }
          .filter { change =>
            (change \ "seq").validate[UpdateSequence] match {
              case JsSuccess(s, _) =>
                currentSinceSeq = s
                true
              case _: JsError =>
                false
            }
          }

      def connectMany(): Source[JsObject, Future[Done]] =
        connectOnce()
          .concat(Source.fromIterator(() => Iterator.continually {
            Source.lazily(() => connectOnce()).initialDelay(reconnectionDelay)
          }).flatMapConcat(identity))

      connectMany()
    }

}
