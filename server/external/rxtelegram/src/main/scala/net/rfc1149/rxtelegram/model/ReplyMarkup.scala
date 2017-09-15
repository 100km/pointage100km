package net.rfc1149.rxtelegram.model

import play.api.libs.json.Writes

trait ReplyMarkup

object ReplyMarkup {
  implicit val replyMarkupWrites: Writes[ReplyMarkup] = Writes {
    case fr: ForceReply ⇒ ForceReply.forceReplyWrites.writes(fr)
    case rkh: ReplyKeyboardHide ⇒ ReplyKeyboardHide.replyKeyboardHideWrites.writes(rkh)
    case rkm: ReplyKeyboardMarkup ⇒ ReplyKeyboardMarkup.replyKeyboardMarkupWrites.writes(rkm)
  }
}
