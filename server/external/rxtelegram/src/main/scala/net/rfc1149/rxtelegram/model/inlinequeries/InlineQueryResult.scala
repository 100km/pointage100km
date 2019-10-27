package net.rfc1149.rxtelegram.model.inlinequeries

import play.api.libs.json.Writes

trait InlineQueryResult

object InlineQueryResult {
  implicit val inlineQueryResultWrites: Writes[InlineQueryResult] = Writes {
    case iqra: InlineQueryResultAudio    => InlineQueryResultAudio.iqraWrites.writes(iqra)
    case iqra: InlineQueryResultArticle  => InlineQueryResultArticle.iqraWrites.writes(iqra)
    case iqrc: InlineQueryResultContact  => InlineQueryResultContact.iqrcWrites.writes(iqrc)
    case iqrd: InlineQueryResultDocument => InlineQueryResultDocument.iqrdWrites.writes(iqrd)
    case iqrg: InlineQueryResultGif      => InlineQueryResultGif.iqrgWrites.writes(iqrg)
    case iqrl: InlineQueryResultLocation => InlineQueryResultLocation.iqrlWrites.writes(iqrl)
    case iqrm: InlineQueryResultMpeg4Gif => InlineQueryResultMpeg4Gif.iqrmWrites.writes(iqrm)
    case iqrp: InlineQueryResultPhoto    => InlineQueryResultPhoto.iqrpWrites.writes(iqrp)
    case iqrv: InlineQueryResultVenue    => InlineQueryResultVenue.iqrvWrites.writes(iqrv)
    case iqrv: InlineQueryResultVideo    => InlineQueryResultVideo.iqrvWrites.writes(iqrv)
    case iqrv: InlineQueryResultVoice    => InlineQueryResultVoice.iqrvWrites.writes(iqrv)
  }
}
