package net.rfc1149.rxtelegram.model

import play.api.libs.json.{ JsSuccess, Reads }

case class Message(message_id: Long, from: Option[User], date: Long, chat: Chat,
  forward_from: Option[User], forward_from_chat: Option[Chat], forward_date: Option[Long],
  reply_to_message: Option[Message], text: Option[String],
  entities: Option[Array[MessageEntity]],
  audio: Option[Audio], voice: Option[Voice], document: Option[Document],
  photo: Option[Array[PhotoSize]], sticker: Option[Sticker],
  video: Option[Video], caption: Option[String],
  contact: Option[Contact], location: Option[Location], venue: Option[Venue],
  new_chat_member: Option[User], left_chat_member: Option[User],
  new_chat_title: Option[String], new_chat_photo: Option[Array[PhotoSize]],
  delete_chat_photo: Option[Boolean], group_chat_created: Option[Boolean],
  supergroup_chat_created: Option[Boolean], channel_chat_created: Option[Boolean],
  migrate_to_chat_id: Option[Long], migrate_from_chat_id: Option[Long],
  pinned_message: Option[Message])

object Message {

  // Since Message has more than 22 fields, the Json.reads[Message] macro cannot be used because
  // apply() won't be defined.

  implicit lazy val messageReads: Reads[Message] = Reads { js ⇒
    JsSuccess(Message(
      message_id = (js \ "message_id").as[Long],
      from = (js \ "from").asOpt[User],
      date = (js \ "date").as[Long],
      chat = (js \ "chat").as[Chat],
      forward_from = (js \ "forward_from").asOpt[User],
      forward_from_chat = (js \ "forward_from_chat").asOpt[Chat],
      forward_date = (js \ "forward_date").asOpt[Long],
      reply_to_message = (js \ "reply_to_message").asOpt[Message],
      text = (js \ "text").asOpt[String],
      entities = (js \ "entities").asOpt[Array[MessageEntity]],
      audio = (js \ "audio").asOpt[Audio],
      voice = (js \ "voice").asOpt[Voice],
      document = (js \ "document").asOpt[Document],
      photo = (js \ "photo").asOpt[Array[PhotoSize]],
      sticker = (js \ "sticker").asOpt[Sticker],
      video = (js \ "video").asOpt[Video],
      caption = (js \ "caption").asOpt[String],
      contact = (js \ "contact").asOpt[Contact],
      location = (js \ "location").asOpt[Location],
      venue = (js \ "venue").asOpt[Venue],
      new_chat_member = (js \ "new_chat_member").asOpt[User],
      left_chat_member = (js \ "left_chat_member").asOpt[User],
      new_chat_title = (js \ "new_chat_title").asOpt[String],
      new_chat_photo = (js \ "new_chat_photo").asOpt[Array[PhotoSize]],
      delete_chat_photo = (js \ "delete_chat_photo").asOpt[Boolean],
      group_chat_created = (js \ "group_chat_created").asOpt[Boolean],
      supergroup_chat_created = (js \ "supergroup_chat_created").asOpt[Boolean],
      channel_chat_created = (js \ "channel_chat_created").asOpt[Boolean],
      migrate_to_chat_id = (js \ "migrate_to_chat_id").asOpt[Long],
      migrate_from_chat_id = (js \ "migrate_from_chat_id").asOpt[Long],
      pinned_message = (js \ "pinned_message").asOpt[Message]))
  }

}
