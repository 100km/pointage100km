package replicate.utils

import java.util.concurrent.TimeUnit

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
    Flow[T].statefulMapConcat { () =>
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

  private class IfUnchangedAfter[T, K](key: T => K, duration: FiniteDuration, maxQueueSize: Int) extends GraphStage[FlowShape[T, T]] {

    assert(maxQueueSize > 0, "maxQueueSize must be positive")

    private val in: Inlet[T] = Inlet("IfUnchangedAfter.in")
    private val out: Outlet[T] = Outlet("IfUnchangedAfter.out")

    private case class Holder(deadline: Long, k: K, element: T)

    private object Enqueued {
      def apply(element: T): Holder = new Holder(System.nanoTime() + duration.toNanos, key(element), element)
    }

    override def createLogic(inheritedAttributes: Attributes) = new TimerGraphStageLogic(shape) {

      private var queue: Queue[Holder] = Queue()
      private var inQueue: Set[K] = Set.empty

      private def sendFromQueue() =
        if (isAvailable(out))
          queue.headOption.map(_.deadline - System.nanoTime()).foreach { remainingNanos =>
            if (remainingNanos <= 0) {
              val (dequeued, newQueue) = queue.dequeue
              queue = newQueue
              inQueue -= dequeued.k
              if (!isClosed(in) && !hasBeenPulled(in))
                pull(in)
              push(out, dequeued.element)
              if (queue.isEmpty && isClosed(in))
                complete(out)
            } else
              scheduleOnce(None, FiniteDuration(remainingNanos, TimeUnit.NANOSECONDS))
          }

      setHandler(in, new InHandler {
        override def onPush() = {
          val element = grab(in)
          val enqueued = Enqueued(element)
          // If the same element (according to the key) was already in the queue, remove it, otherwise
          // remember that it will be in the queue.
          if (inQueue.contains(enqueued.k))
            queue = queue.filterNot(_.k == enqueued.k)
          else
            inQueue += enqueued.k
          // Start the timer if this is the first element in the queue and the output port is waiting
          if (queue.isEmpty && isAvailable(out))
            scheduleOnce(None, duration)
          queue = queue.enqueue(enqueued)
          if (queue.size < maxQueueSize)
            pull(in)
        }

        override def onUpstreamFinish() =
          if (queue.isEmpty)
            complete(out)
      })

      setHandler(out, new OutHandler {
        override def onPull() = sendFromQueue()
      })

      override def preStart() = pull(in)

      override protected def onTimer(timerKey: Any) = sendFromQueue()
    }

    override def shape = FlowShape(in, out)
  }

  /**
   * Wait for some time before letting an element flow through. If an element with the same key arrives in
   * the meantime or any time before downstream pulls the previous one, discard the previous one and start
   * the waiting period from scratch.
   *
   * @param key a function deriving the key for an element
   * @param duration the element retention time
   * @param maxQueueSize the maximum number of elements in transit (with unique keys) before maxpressure triggers
   *                     upstream
   * @tparam T the type of the elements
   * @tparam K the type of the keys
   * @return the delayed and possibly reduced stream
   */
  def ifUnchangedAfter[T, K](key: T => K, duration: FiniteDuration, maxQueueSize: Int): Flow[T, T, NotUsed] =
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
    Flow[T].sliding(2).collect { case Seq(a, b) if a != b => (a, b) }

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
            case None =>
              push(out, value)
              previousValue = Some(value)
            case Some(previous) if ord.compare(previous, value) <= ordCriterium =>
              push(out, value)
              previousValue = Some(value)
            case _ =>
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
