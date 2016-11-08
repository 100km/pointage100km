package net.rfc1149.rxtelegram.model

import play.api.libs.json.{Json, Reads}

case class User(id: Long, first_name: String, last_name: Option[String], username: Option[String]) extends Equals {

  override def canEqual(other: Any): Boolean = other.isInstanceOf[User]

  override def equals(other: Any): Boolean = other match {
    case that: User ⇒ id == that.id
    case _          ⇒ false
  }

  def fullName: String = first_name + last_name.fold("")(' ' + _)
}

object User {
  implicit val userReads: Reads[User] = Json.format[User]
}