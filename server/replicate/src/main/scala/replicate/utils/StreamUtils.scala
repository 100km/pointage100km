package replicate.utils

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.collection.immutable.Queue
import scala.concurrent.duration.{Deadline, FiniteDuration}

trait StreamUtils {

  /**
   * Filter the upstream elements so that one goes through only if a specified delay has
   * expired since the last element which went through.
   *
   * @param interSampleDelay the time during which upstream elements are ignored
   * @tparam T the type of stream elements
   * @return the filtered stream
   */
  def sample[T](interSampleDelay: FiniteDuration): Flow[T, T, NotUsed] =
    Flow[T].statefulMapConcat { () ⇒
      var nextSampleDate = Deadline.now
      def filter(t: T): List[T] = {
        if (Deadline.now < nextSampleDate)
          Nil
        else {
          nextSampleDate = Deadline.now + interSampleDelay
          List(t)
        }
      }
      filter
    }

  private class IfUnchangedAfter[T, K](key: T ⇒ K, duration: FiniteDuration, maxQueueSize: Int) extends GraphStage[FlowShape[T, T]] {

    assert(maxQueueSize > 0, "maxQueueSize must be positive")

    val in: Inlet[T] = Inlet("IfUnchangedAfter.in")
    val out: Outlet[T] = Outlet("IfUnchangedAfter.out")

    override def createLogic(inheritedAttributes: Attributes) = new TimerGraphStageLogic(shape) {

      private var onHold: Map[K, T] = Map()
      private var queue: Queue[T] = Queue()
      private var slotsRemaining: Int = maxQueueSize

      private def sendFromQueue() =
        if (queue.nonEmpty && isAvailable(out)) {
          val (element, newQueue) = queue.dequeue
          push(out, element)
          queue = newQueue
          if (queue.isEmpty && onHold.isEmpty && isClosed(in))
            complete(out)
          slotsRemaining += 1
          if (!isClosed(in) && !hasBeenPulled(in))
            pull(in)
        }

      setHandler(in, new InHandler {
        override def onPush() = {
          val element = grab(in)
          val k = key(element)
          // Do not decrement the number of available slots if we already have a (to be cancelled)
          // element for this key.
          if (onHold.contains(k)) cancelTimer(k, onHold(k)) else slotsRemaining -= 1
          onHold += k → element
          scheduleOnce((k, element), duration)
          if (slotsRemaining > 0)
            pull(in)
        }

        override def onUpstreamFinish() =
          if (queue.isEmpty && onHold.isEmpty)
            complete(out)
      })

      setHandler(out, new OutHandler {
        override def onPull() = sendFromQueue()
      })

      override def preStart() = pull(in)

      override protected def onTimer(timerKey: Any) = {
        val (k, element) = timerKey.asInstanceOf[(K, T)]
        queue = queue.enqueue(element)
        onHold -= k
        sendFromQueue()
      }
    }

    override def shape = FlowShape(in, out)
  }

  /**
   * Wait for some time before letting an element flow through. If an element with the same key arrives in
   * the meantime, discard the previous element and start the waiting period from scratch.
   *
   * @param key a function deriving the key for an element
   * @param duration the element retention time
   * @param maxQueueSize the maximum number of elements in transit before maxpressure triggers
   * @tparam T the type of the elements
   * @tparam K the type of the keys
   * @return the delayed and possibly reduced stream
   */
  def ifUnchangedAfter[T, K](key: T ⇒ K, duration: FiniteDuration, maxQueueSize: Int): Flow[T, T, NotUsed] =
    Flow[T].via(new IfUnchangedAfter[T, K](key, duration, maxQueueSize))

  private class IdleAlert[T](timeout: FiniteDuration, alert: T) extends GraphStage[FlowShape[T, T]] {

    val in: Inlet[T] = Inlet("IdleAlert.in")
    val out: Outlet[T] = Outlet("IdleAlert.out")

    override def createLogic(inheritedAttributes: Attributes) = new TimerGraphStageLogic(shape) {

      var element: Option[T] = None
      var signalled: Boolean = false

      setHandler(in, new InHandler {
        override def onPush() = {
          if (!isClosed(out)) {
            push(out, grab(in))
            cancelTimer(None)
            signalled = false
          } else
            element = Some(grab(in))
        }
      })

      setHandler(out, new OutHandler {
        override def onPull() =
          if (element.isDefined) {
            push(out, element.get)
            element = None
            cancelTimer(None)
            signalled = false
          } else {
            if (!hasBeenPulled(in))
              pull(in)
            if (!signalled)
              scheduleOnce(None, timeout)
          }

        override def onDownstreamFinish() =
          cancelTimer(None)
      })

      override protected def onTimer(timerKey: Any): Unit =
        if (!isClosed(out)) {
          signalled = true
          push(out, alert)
        }

      (in, out)
    }

    override def shape = FlowShape(in, out)
  }

  /**
   * Insert an alert element into a stream if the downstream demand is not satisfied during
   * the specified timeout. After the alert has been inserted into the stream, it will not
   * be inserted again until an upstream element flows through.
   *
   * @param timeout the timeout after which the alert element is inserted
   * @param alert the alert element
   * @tparam T the type of stream elements
   * @return the enriched stream
   */
  def idleAlert[T](timeout: FiniteDuration, alert: T): Flow[T, T, NotUsed] =
    Flow[T].via(new IdleAlert[T](timeout, alert))

  /**
   * Return sliding pairs of upstream after filtering out duplicates.
   *
   * @tparam T the type of stream elements
   * @return the stream of paired elements
   */
  def pairDifferent[T]: Flow[T, (T, T), NotUsed] =
    Flow[T].sliding(2).collect { case Seq(a, b) if a != b ⇒ (a, b) }

  private class OnlyIncreasing[T](strict: Boolean)(implicit ord: Ordering[T]) extends GraphStage[FlowShape[T, T]] {

    private[this] val in: Inlet[T] = Inlet("OnlyIncreasing.in")
    private[this] val out: Outlet[T] = Outlet("OnlyIncreasing.out")

    override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {

      private[this] val ordCriterium: Int = if (strict) -1 else 0
      private[this] var previousValue: Option[T] = None

      setHandler(in, new InHandler {
        override def onPush() = {
          val value = grab(in)
          previousValue match {
            case None ⇒
              push(out, value)
              previousValue = Some(value)
            case Some(previous) if ord.compare(previous, value) <= ordCriterium ⇒
              push(out, value)
              previousValue = Some(value)
            case _ ⇒
              pull(in)
          }
        }
      })

      setHandler(out, new OutHandler {
        override def onPull() = pull(in)
      })
    }

    override def shape = FlowShape(in, out)
  }

  /**
   * Filter upstream so that it only contains increasing elements according to the specified
   * ordering.
   *
   * @param strict `true` if the comparison must be strict, `false` if equal elements can be emitted
   * @param ord the Ordering instance to use for comparison
   * @tparam T the type of stream elements
   * @return the stream of increasing elements
   */
  def onlyIncreasing[T](strict: Boolean)(implicit ord: Ordering[T]): Flow[T, T, NotUsed] =
    Flow[T].via(new OnlyIncreasing[T](strict)(ord))
}

object StreamUtils extends StreamUtils
