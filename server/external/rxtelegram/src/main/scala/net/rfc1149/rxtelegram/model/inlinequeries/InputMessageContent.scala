package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.Writes

trait InputMessageContent

object InputMessageContent {

  implicit val imcWrites: Writes[InputMessageContent] = Writes {
    case icmc: InputContactMessageContent  => InputContactMessageContent.icmcWrites.writes(icmc)
    case ilmc: InputLocationMessageContent => InputLocationMessageContent.ilmcWrites.writes(ilmc)
    case itmc: InputTextMessageContent     => InputTextMessageContent.itmcWrites.writes(itmc)
    case ivmc: InputVenueMessageContent    => InputVenueMessageContent.ivmcWrites.writes(ivmc)
  }
}
