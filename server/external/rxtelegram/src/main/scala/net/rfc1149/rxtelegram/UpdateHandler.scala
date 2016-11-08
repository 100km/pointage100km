package net.rfc1149.rxtelegram

import net.rfc1149.rxtelegram.model.{ChosenInlineResult, Message, Update}
import net.rfc1149.rxtelegram.model.inlinequeries.InlineQuery

trait UpdateHandler {

  protected[this] def handleMessage(message: Message): Unit

  protected[this] def handleInlineQuery(inlineQuery: InlineQuery): Unit = sys.error("unhandled inline query")

  protected[this] def handleChosenInlineResult(chosenInlineResult: ChosenInlineResult): Unit = sys.error("unhandled chosen inline result")

  def handleUpdate(update: Update): Unit = {
    update.message foreach handleMessage
    update.inline_query foreach handleInlineQuery
    update.chosen_inline_result foreach handleChosenInlineResult
  }

}
