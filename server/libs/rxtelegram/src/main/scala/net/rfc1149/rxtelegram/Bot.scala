package net.rfc1149.rxtelegram

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.{MessageEntity ⇒ MEntity, _}
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import net.rfc1149.rxtelegram.model._
import net.rfc1149.rxtelegram.model.inlinequeries.InlineQueryResult
import net.rfc1149.rxtelegram.model.media.Media
import net.rfc1149.rxtelegram.utils._
import play.api.libs.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait Bot {

  implicit val actorSystem: ActorSystem
  implicit val ec: ExecutionContext
  implicit val fm: Materializer

  import Bot._

  val token: String

  private[this] var offset: Long = -1

  private[this] def send(methodName: String, fields: Seq[(String, String)] = Seq(), media: Option[MediaParameter] = None,
    potentiallyBlocking: Boolean = false): Future[JsValue] =
    sendInternal(methodName, buildEntity(fields, media), potentiallyBlocking = potentiallyBlocking)

  def sendToMessage(data: Command): Future[Message] = send(data).map(_.as[Message])

  def send(data: Command): Future[JsValue] = {
    sendInternal(data.methodName, data.buildEntity(includeMethod = false)).map(checkResult)
  }

  private[this] lazy val host = "api.telegram.org"
  private[this] lazy val port = 443
  private[this] lazy val apiPool = Http().cachedHostConnectionPoolHttps[Any](host, port)
  private[this] lazy val apiFlow = Http().outgoingConnectionHttps(host, port)

  private[this] def sendRaw(request: HttpRequest, potentiallyBlocking: Boolean = false): Future[HttpResponse] =
    if (potentiallyBlocking)
      Source.single(request).via(apiFlow).runWith(Sink.head)
    else
      Source.single((request, None)).via(apiPool).map(_._1.get).runWith(Sink.head)

  private[this] def sendInternal(methodName: String, entity: MEntity, potentiallyBlocking: Boolean = false): Future[JsValue] = {
    val request = HttpRequest(
      method  = HttpMethods.POST,
      uri     = s"https://api.telegram.org/bot$token/$methodName",
      headers = List(`Accept`(MediaTypes.`application/json`)),
      entity  = entity
    )
    sendRaw(request, potentiallyBlocking = potentiallyBlocking).flatMap { response ⇒
      response.status match {
        case status if status.isFailure() ⇒ throw HTTPException(status.toString())
        case status ⇒
          try {
            Unmarshal(response.entity).to[JsValue]
          } catch {
            case t: Throwable ⇒
              throw JSONException(t)
          }
      }
    }
  }

  def getMe: Future[User] =
    send("getMe").map { json ⇒
      (json \ "result").as[User]
    }

  def getUpdates(limit: Long = 100, timeout: FiniteDuration = 0.seconds): Future[List[Update]] =
    send("getUpdates", (offset + 1).toField("offset") ++ limit.toField("limit", 100) ++ timeout.toSeconds.toField("timeout", 0),
      potentiallyBlocking = true)
      .map { json ⇒ (json \ "result").as[List[Update]] }

  def getUserProfilePhotos(user_id: Long, offset: Long = 0, limit: Long = 100): Future[UserProfilePhotos] =
    send("getUserProfilePhotos", user_id.toField("user_id") ++ offset.toField("offset", 0) ++ limit.toField("limit", 100))
      .map { json ⇒ (json \ "result").as[UserProfilePhotos] }

  def getFile(file_id: String): Future[(File, Option[ResponseEntity])] = {
    send("getFile", file_id.toField("file_id")).map { json ⇒ (json \ "result").as[File] }.flatMap { file ⇒
      file.file_path match {
        case Some(path) ⇒
          sendRaw(HttpRequest(method = HttpMethods.GET, uri = s"https://api.telegram.org/file/bot$token/$path",
            headers = List(Accept(MediaRanges.`*/*`)))).map(response ⇒ (file, Some(response.entity)))
        case None ⇒
          FastFuture.successful((file, None))
      }
    }
  }

  protected[this] def acknowledgeUpdate(update: Update): Unit =
    offset = offset.max(update.update_id)

  def setWebhook(uri: String, certificate: Option[Media] = None): Future[JsValue] =
    send("setWebhook", Seq("url" → uri), certificate.map(MediaParameter("certificate", _)))

}

object Bot extends PlayJsonSupport {

  sealed trait ChatAction {
    val action: String
  }
  case object Typing extends ChatAction {
    val action = "typing"
  }
  case object UploadPhoto extends ChatAction {
    val action = "upload_photo"
  }
  case object RecordVideo extends ChatAction {
    val action = "record_video"
  }
  case object UploadVideo extends ChatAction {
    val action = "upload_video"
  }
  case object RecordAudio extends ChatAction {
    val action = "record_audio"
  }
  case object UploadAudio extends ChatAction {
    val action = "upload_audio"
  }
  case object UploadDocument extends ChatAction {
    val action = "upload_document"
  }
  case object FindLocation extends ChatAction {
    val action = "find_location"
  }

  case class MediaParameter(fieldName: String, media: Media) {
    def toBodyPart = media.toBodyPart(fieldName)
  }

  def checkResult(js: JsValue): JsValue =
    if ((js \ "ok").as[Boolean])
      (js \ "result").as[JsValue]
    else
      throw APIException((js \ "description").as[String])

  sealed trait TelegramException extends Exception

  case class HTTPException(status: String) extends TelegramException {
    override val toString = s"HTTPException($status)"
  }
  case class APIException(description: String) extends TelegramException {
    override val toString = s"APIException($description)"
  }
  case class JSONException(inner: Throwable) extends TelegramException {
    override val toString = s"JSONException($inner)"
  }

  sealed abstract class Target(chatId: Option[String] = None, messageId: Option[Long] = None,
      inlineMessageId: Option[String] = None, disableNotification: Boolean = false) {
    def toFields: List[(String, String)] = disableNotification.toField("disable_notification", false) ++
      (inlineMessageId match {
        case Some(id) ⇒ inlineMessageId.toField("inline_message_id")
        case None     ⇒ chatId.toField("chat_id") ++ messageId.toField("in_reply_to_message_id")
      })

    def isInlineMessageId: Boolean = inlineMessageId.isDefined

    require(inlineMessageId.isDefined == chatId.isEmpty, "exactly one of inlineMessageId or chatId must be defined")
  }

  case class To(chatId: String, disableNotification: Boolean = false) extends Target(chatId = Some(chatId), disableNotification = disableNotification)

  object To {
    def apply(chat_id: Long): To = To(chat_id.toString)
    def apply(message: Message): To = To(message.chat.id)
    def apply(chat: Chat): To = To(chat.id)
    def apply(user: User): To = To(user.id)
  }

  case class Reply(chatId: String, messageId: Long) extends Target(chatId = Some(chatId), messageId = Some(messageId))

  object Reply {
    def apply(message: Message): Reply = Reply(message.chat.id.toString, message.message_id)
  }

  case class InlineMessageId(inlineMessageId: String) extends Target(inlineMessageId = Some(inlineMessageId))

  sealed trait Command {
    def buildEntity(includeMethod: Boolean): MEntity
    val methodName: String
  }

  sealed trait Action extends Command {
    val methodName: String
    val replyMarkup: Option[ReplyMarkup]
    val supportsInlineMessageId: Boolean = false

    val fields: List[(String, String)]
    val media: Option[MediaParameter] = None

    def buildEntity(target: Target, includeMethod: Boolean) = {
      assert(!target.isInlineMessageId || supportsInlineMessageId, "this action does not support inline_message_id targets")
      val allFields = fields ++ replyMarkup.toField("reply_markup") ++ target.toFields ++ (if (includeMethod) Seq("method" → methodName) else Seq())
      Bot.buildEntity(allFields, media)
    }

    override def buildEntity(includeMethod: Boolean) = {
      val allFields = fields ++ replyMarkup.toField("reply_markup") ++ (if (includeMethod) Seq("method" → methodName) else Seq())
      Bot.buildEntity(allFields, media)
    }

    protected def namedMedia(name: String, media: Media) = Some(MediaParameter(name, media))
  }

  case class Targetted(target: Target, action: Action) extends Command {
    override def buildEntity(includeMethod: Boolean) =
      action.buildEntity(target, includeMethod)

    val methodName = action.methodName
  }

  case class ActionForwardMessage(message: Reply) extends Action {
    val methodName = "forwardMessage"
    val replyMarkup = None
    val fields = message.chatId.toField("from_chat_id") ++ message.messageId.toField("message_id")
  }

  object ActionForwardMessage {
    def apply(message: Message): ActionForwardMessage = ActionForwardMessage(Reply(message))
  }

  case class ActionMessage(text: String, disable_web_page_preview: Boolean = false,
      parse_mode: ParseMode = ParseModeDefault, replyMarkup: Option[ReplyMarkup] = None) extends Action {
    val methodName = "sendMessage"
    val fields = text.toField("text") ++ disable_web_page_preview.toField("disable_web_page_preview", false) ++
      parse_mode.option.toField("parse_mode")
  }

  case class ActionPhoto(photo: Media, caption: Option[String] = None, replyMarkup: Option[ReplyMarkup] = None) extends Action {
    val methodName = "sendPhoto"
    val fields = caption.toField("caption")
    override val media = namedMedia("photo", photo)
  }

  case class ActionAudio(audio: Media, duration: Option[FiniteDuration] = None,
      performer: Option[String], title: Option[String], replyMarkup: Option[ReplyMarkup] = None) extends Action {
    val methodName = "sendAudio"
    val fields = duration.toField("duration") ++ performer.toField("performer") ++ title.toField("title")
    override val media = namedMedia("audio", audio)
  }

  case class ActionVoice(voice: Media, duration: Option[FiniteDuration] = None, replyMarkup: Option[ReplyMarkup] = None) extends Action {
    val methodName = "sendVoice"
    val fields = duration.toField("duration")
    override val media = namedMedia("voice", voice)
  }

  case class ActionDocument(document: Media, replyMarkup: Option[ReplyMarkup] = None) extends Action {
    val methodName = "sendDocument"
    val fields = Nil
    override val media = namedMedia("document", document)
  }

  case class ActionSticker(sticker: Media, replyMarkup: Option[ReplyMarkup] = None) extends Action {
    val methodName = "sendSticker"
    val fields = Nil
    override val media = namedMedia("sticker", sticker)
  }

  case class ActionVideo(video: Media, duration: Option[FiniteDuration] = None,
      caption: Option[String], replyMarkup: Option[ReplyMarkup] = None) extends Action {
    val methodName = "sendVideo"
    val fields = duration.map(_.toSeconds).toField("duration") ++ caption.toField("caption")
    override val media = namedMedia("video", video)
  }

  case class ActionLocation(location: (Double, Double), replyMarkup: Option[ReplyMarkup] = None) extends Action {
    val methodName = "sendLocation"
    val fields = location._1.toField("latitude") ++ location._2.toField("longitude")
  }

  object ActionLocation {
    def apply(location: Location): ActionLocation = ActionLocation((location.latitude, location.longitude))
    def apply(location: Location, replyMarkup: ReplyMarkup): ActionLocation =
      ActionLocation((location.latitude, location.longitude), Some(replyMarkup))
  }

  case class ActionVenue(location: (Double, Double), title: String, address: String, foursquareId: Option[String] = None,
      replyMarkup: Option[ReplyMarkup] = None) extends Action {
    val methodName = "sendVenue"
    val fields = location._1.toField("latitude") ++ location._2.toField("longitude") ++ title.toField("title") ++
      address.toField("address") ++ foursquareId.toField("foursquare_id")
  }

  object ActionVenue {
    def apply(venue: Venue): ActionVenue =
      ActionVenue((venue.location.latitude, venue.location.longitude), venue.title, venue.address, venue.foursquare_id)
    def apply(venue: Venue, replyMarkup: ReplyMarkup): ActionVenue =
      ActionVenue((venue.location.latitude, venue.location.longitude), venue.title, venue.address, venue.foursquare_id, Some(replyMarkup))
  }

  case class ActionChatAction(action: ChatAction) extends Action {
    val methodName = "sendChatAction"
    val replyMarkup = None
    val fields = action.action.toField("action")
  }

  case class ActionAnswerInlineQuery(inlineQueryId: String, results: Seq[InlineQueryResult],
      cacheTime: Long = 300, isPersonal: Boolean = false, nextOffset: Option[String] = None,
      switchPmTextAndParameter: Option[(String, String)] = None) extends Action {

    require(results.size <= 50, "answerInlineQuery cannot return more than 50 results")

    val methodName = "answerInlineQuery"
    val replyMarkup = None
    val fields = inlineQueryId.toField("inline_query_id") ++
      Json.stringify(Json.toJson(results)).toField("results") ++
      cacheTime.toField("cache_time") ++ isPersonal.toField("is_personal", false) ++ nextOffset.toField("next_offset") ++
      switchPmTextAndParameter.map(_._1).toField("switch_pm_text") ++ switchPmTextAndParameter.map(_._2).toField("switch_pm_parameter")
  }

  case class ActionAnswerCallbackQuery(callbackQueryId: String, text: Option[String] = None, showAlert: Boolean = false)
      extends Action {
    val methodName = "answerCallbackQuery"
    val replyMarkup = None
    val fields = callbackQueryId.toField("callback_query_id") ++ text.toField("text") ++ showAlert.toField("show_alert", false)
  }

  case class ActionEditMessageText(text: String, parseMode: Option[ParseMode] = None,
      disableWebPagePreview: Boolean = false, replyMarkup: Option[ReplyMarkup] = None) extends Action {
    val methodName = "editMessageText"
    val fields = text.toField("text") ++
      parseMode.toField("parse_mode") ++ disableWebPagePreview.toField("disable_web_page_preview", false) ++
      replyMarkup.toField("reply_markup")
    override val supportsInlineMessageId = true
  }

  case class ActionEditMessageCaption(caption: Option[String] = None, replyMarkup: Option[ReplyMarkup] = None) extends Action {
    val methodName = "editMessageCaption"
    val fields = caption.toField("caption") ++ replyMarkup.toField("reply_markup")
    override val supportsInlineMessageId = true
  }

  case class ActionEditMessageReplyMarkup(replyMarkup: Option[ReplyMarkup] = None) extends Action {
    val methodName = "editMessageReplyMarkup"
    val fields = replyMarkup.toField("reply_markup")
    override val supportsInlineMessageId = true
  }

  case class ActionKickChatMember() extends Action {
    val methodName = "kickChatMember"
    val replyMarkup = None
    val fields = Nil
  }

  case class ActionUnbanChatMember() extends Action {
    val methodName = "unbanChatMember"
    val replyMarkup = None
    val fields = Nil
  }

  sealed trait ParseMode {
    val option: Option[String]
  }

  object ParseMode {
    implicit val parseModeWrites: Writes[ParseMode] = Writes { pm ⇒ Json.toJson(pm.option) }
  }

  object ParseModeDefault extends ParseMode {
    val option = None
  }

  object ParseModeMarkdown extends ParseMode {
    val option = Some("Markdown")
  }

  object ParseModeHTML extends ParseMode {
    val option = Some("HTML")
  }

  def buildEntity(fields: Seq[(String, String)], media: Option[MediaParameter]): MEntity = {
    if (media.isDefined) {
      val data = fields.map { case (k, v) ⇒ BodyPart(k, HttpEntity(v)) }
      Multipart.FormData(media.get.toBodyPart :: data.toList: _*).toEntity()
    } else if (fields.isEmpty)
      HttpEntity.Empty
    else
      FormData(fields.toMap).toEntity
  }

}

