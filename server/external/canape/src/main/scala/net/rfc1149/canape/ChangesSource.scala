package net.rfc1149.canape

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.{ Done, NotUsed }
import net.ceedubs.ficus.Ficus._
import net.rfc1149.canape.Couch.StatusError
import net.rfc1149.canape.Database.{ FromNow, UpdateSequence }
import net.rfc1149.canape.utils.DelayedSource._
import play.api.libs.json.{ JsError, JsObject, JsSuccess, Json }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ Future, Promise }

object ChangesSource {

  def changesSource(database: Database, params: Map[String, String] = Map(), extraParams: JsObject = Json.obj(),
    sinceSeq: UpdateSequence = FromNow)(implicit system: ActorSystem): Source[JsObject, Future[Done]] =
    {
      implicit val ec = system.dispatcher
      val reconnectionDelay = database.couch.canapeConfig.as[FiniteDuration]("changes-source.reconnection-delay")
      var currentSinceSeq = sinceSeq

      def connectOnce(): Source[JsObject, Future[Done]] = {
        val p = Promise[Done]
        Source(1 to 2).flatMapConcat {
          case 1 ⇒
            database.continuousChanges(params + ("since" → currentSinceSeq.toString), extraParams)
              .mapMaterializedValue { d ⇒
                p.completeWith(d)
                d
              }
              .filter { change ⇒
                (change \ "seq").validate[UpdateSequence] match {
                  case JsSuccess(s, _) ⇒
                    currentSinceSeq = s
                    true
                  case _: JsError ⇒
                    (change \ "last_seq").validate[UpdateSequence] match {
                      case JsSuccess(_, _) ⇒
                        throw new TimeoutException()
                      case _ ⇒
                        false
                    }
                }
              }
          case _ ⇒
            Source.failed(new TimeoutException())
        }.mapMaterializedValue(_ ⇒ p.future)
      }

      def connectMany(): Source[JsObject, Future[Done]] =
        connectOnce().recoverWithRetries(-1, {
          case t if !t.isInstanceOf[StatusError] ⇒
            connectOnce().delayConnection(reconnectionDelay).mapMaterializedValue(_ ⇒ NotUsed)
        })

      connectMany()
    }

}
