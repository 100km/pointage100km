package net.rfc1149.canape
package utils

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

object ExhaustInput {
  def apply[T](): ExhaustInput[T] = new ExhaustInput[T]
}

class ExhaustInput[T] extends GraphStage[FlowShape[T, T]] {
  val in: Inlet[T] = Inlet[T]("ExhaustInput.in")
  val out: Outlet[T] = Outlet[T]("ExhaustInput.out")
  override val shape: FlowShape[T, T] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    var outputClosed: Boolean = false

    setHandler(in, new InHandler {
      override def onPush() =
        if (outputClosed)
          pull(in)
        else
          push(out, grab(in))

      override def onUpstreamFinish(): Unit = completeStage()
    })

    setHandler(out, new OutHandler {
      override def onPull() = pull(in)

      override def onDownstreamFinish = {
        outputClosed = true
        pull(in)
      }
    })
  }
}

