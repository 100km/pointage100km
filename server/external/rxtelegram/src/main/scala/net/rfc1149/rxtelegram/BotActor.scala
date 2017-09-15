package net.rfc1149.rxtelegram

import akka.actor.Status.Failure
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Stash }
import akka.pattern.pipe
import akka.stream.scaladsl.Sink
import akka.stream.{ ActorMaterializer, Materializer }
import net.rfc1149.rxtelegram.Bot.{ ActionAnswerInlineQuery, Command }
import net.rfc1149.rxtelegram.model._
import net.rfc1149.rxtelegram.model.media.Media

import scala.concurrent.{ ExecutionContext, Future }

abstract class BotActor(val token: String, val options: Options) extends Actor with ActorLogging with Stash with Bot with UpdateHandler {

  import BotActor._

  implicit val actorSystem: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val fm: Materializer = ActorMaterializer()

  protected[this] var me: User = _

  protected[this] def handleOther(other: Any): Unit = {
    log.info("received unknown content: {}", other)
  }

  override def preStart() =
    self ! GetMyself

  override def receive = {
    case GetMyself ⇒
      getMe.pipeTo(self)

    case user: User ⇒
      me = user
      setWebhook("")
      unstashAll()
      context.become(receiveIKnowMe)
      UpdateSource(token, options).runWith(Sink.actorRefWithAck(self, Init, Ack, Complete, Fail))

    case Failure(t) ⇒
      log.error(t, "cannot get information about myself")
      throw t

    case _ ⇒
      stash()
  }

  private[this] var ongoingSend: Boolean = false
  private[this] val sendQueue = new scala.collection.mutable.Queue[(() ⇒ Future[_], ActorRef)]

  /**
   * Perform one send operation and send a `Done` message to `self`.
   *
   * @param f the send operation
   * @param r the actor to send the result of the operation to
   */
  private[this] def computeAndSendResult(f: Future[_], r: ActorRef): Unit = {
    assert(!ongoingSend)
    ongoingSend = true
    pipe(f).to(r)
    f.onComplete(_ ⇒ self ! Done)
  }

  /**
   * Serialize send operations so that they happen in order.
   *
   * @param f the send operation
   * @param r the actor to send the result of the operation to
   */
  private[this] def attemptSend(f: () ⇒ Future[_], r: ActorRef): Unit =
    if (ongoingSend)
      sendQueue += ((f, r))
    else
      computeAndSendResult(f(), r)

  def receiveIKnowMe: Receive = {
    case GetMe ⇒
      sender ! me

    case Init ⇒
      sender ! Ack

    case Complete ⇒
      log.debug("end of updates stream from Telegram")
      context.stop(self)

    case Fail(t) ⇒
      log.error(t, "error when fetching updates stream from Telegram")
      throw t

    case update: Update ⇒
      sender ! Ack
      handleUpdate(update)

    case data: ActionAnswerInlineQuery ⇒
      attemptSend(() ⇒ send(data), sender())

    case data: Command ⇒
      attemptSend(() ⇒ sendToMessage(data), sender())

    case Done ⇒
      // End of a serialized operation. Start next one if needed.
      ongoingSend = false
      sendQueue.dequeueFirst(_ ⇒ true).foreach { case (f, r) ⇒ computeAndSendResult(f(), r) }

    case SetWebhook(uri, certificate) ⇒
      setWebhook(uri, certificate).pipeTo(sender())

    case GetUserProfilePhotos(user, offset, limit) ⇒
      getUserProfilePhotos(user.id, offset, limit).pipeTo(sender())

    case GetFile(file_id) ⇒
      getFile(file_id).pipeTo(sender())

    case other ⇒
      handleOther(other)
  }

}

object BotActor {

  // ActorRefWithAck protocol
  case object Init
  case object Ack
  case object Complete
  case class Fail(t: Throwable)

  case object GetMe
  private case object GetMyself
  private case object Done

  case class SetWebhook(uri: String = "", certificate: Option[Media] = None)

  case class GetUserProfilePhotos(user: User, offset: Long = 0, limit: Long = 100)

  // Answer: (File, Option[ResponseEntity])
  case class GetFile(file_id: String)

}
