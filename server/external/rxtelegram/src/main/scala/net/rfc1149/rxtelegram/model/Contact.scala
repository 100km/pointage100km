package net.rfc1149.rxtelegram.model

import play.api.libs.json.{Json, Reads}

case class Contact(phone_number: String, first_name: String, last_name: Option[String], user_id: Option[Long])

object Contact {
  implicit val contactReads: Reads[Contact] = Json.reads[Contact]
}
