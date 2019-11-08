import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.Materializer
import org.specs2.mutable._
import replicate.utils.StreamUtils._

import scala.concurrent.Await
import scala.concurrent.duration._

class StreamUtilsSpec extends Specification {

  trait WithActorSystem extends After {
    // FIXME: some things are odds with fuzzing mode
    /*
    val config = ConfigFactory.parseString(
      """
        | akka.stream.materializer.debug.fuzzing-mode = on
        | akka.stream.secret-test-fuzzing-warning-disable = on
      """.stripMargin)
    implicit val system = ActorSystem("test-system", config)
    */
    implicit val system = ActorSystem()

    override def after() = system.terminate()
  }

  "sample()" should {

    "filter the right number of elements" in new WithActorSystem {
      val source = Source.tick(0.second, 100.milliseconds, 1).zipWith(Source(1 to 100)) { case (_, n) => n }
      // This will get the samples at 0, 200, 300, and 040 milliseconds, namely 1, 3, 5, and 7
      val result = source.via(sample(140.milliseconds)).take(4).runWith(Sink.reduce[Int](_ + _))
      Await.result(result, 1.second) must be equalTo 16
    }

    "propagate an upstream completion without delay" in new WithActorSystem {
      val result = Source.single(1).via(sample(1.second)).runWith(Sink.reduce[Int](_ + _))
      Await.result(result, 100.milliseconds) must be equalTo 1
    }

    "propagate an upstream error without delay" in new WithActorSystem {
      val source = Source.single(1) ++ Source.failed(new RuntimeException("marker"))
      val result = source.via(sample(1.second)).runWith(Sink.reduce[Int](_ + _))
      Await.result(result, 100.milliseconds) must throwA[RuntimeException]("marker")
    }

    "propagate an initial error" in new WithActorSystem {
      val result = Source.failed[Int](new RuntimeException("marker")).via(sample(1.second)).runWith(Sink.head)
      Await.result(result, 2.seconds) must throwA[RuntimeException]("marker")
    }

  }

  "ifUnchangedAfter()" should {

    def probes()(implicit system: ActorSystem, fm: Materializer): (TestPublisher.Probe[String], TestSubscriber.Probe[String]) =
      TestSource.probe[String].via(ifUnchangedAfter[String, Char](_.head, 100.milliseconds, 10)).toMat(TestSink.probe[String])(Keep.both).run()

    def delay(duration: FiniteDuration): Unit =
      try Thread.sleep(duration.toMillis)
      catch { case _: InterruptedException => }

    "close an empty stream" in new WithActorSystem {
      val (upstream, downstream) = probes()
      upstream.sendComplete()
      downstream.expectSubscription()
      downstream.expectComplete()
    }

    "let a single element go through" in new WithActorSystem {
      val (upstream, downstream) = probes()
      upstream.sendNext("foobar").sendComplete()
      downstream.request(2).expectNoMessage(50.milliseconds).expectNext("foobar").expectComplete()
    }

    "propagate errors immediately" in new WithActorSystem {
      val (upstream, downstream) = probes()
      val error = new RuntimeException("random error")
      upstream.sendNext("foobar").sendError(error)
      downstream.request(2).expectError(error)
    }

    "let elements flow through in order if keys are distincts" in new WithActorSystem {
      val (upstream, downstream) = probes()
      upstream.sendNext("foo").sendNext("bar").sendNext("xyzzy").sendComplete()
      downstream.request(4).expectNoMessage(50.milliseconds).expectNext("foo", "bar", "xyzzy").expectComplete()
    }

    "replace elements with the same key" in new WithActorSystem {
      val (upstream, downstream) = probes()
      upstream.sendNext("foo").sendNext("bar").sendNext("final").sendNext("xyzzy").sendComplete()
      downstream.request(5).expectNoMessage(50.milliseconds).expectNext("bar", "final", "xyzzy").expectComplete()
    }

    "replace elements with the same key if the delay has expired if they have not been pulled downstream" in new WithActorSystem {
      val (upstream, downstream) = probes()
      upstream.sendNext("foo").sendNext("bar")
      delay(200.milliseconds)
      upstream.sendNext("final").sendNext("xyzzy").sendComplete()
      downstream.request(5).expectNext("bar", "final", "xyzzy").expectComplete()
    }

    "not replace elements with the same key if the delay has expired and they are pulled downstream" in new WithActorSystem {
      val (upstream, downstream) = probes()
      upstream.sendNext("foo").sendNext("bar")
      downstream.request(5).expectNext("foo")
      upstream.sendNext("final").sendNext("xyzzy").sendComplete()
      downstream.expectNext("bar", "final", "xyzzy").expectComplete()
    }

    "backpressure the input when the queue is full" in new WithActorSystem {
      val (upstream, downstream) = probes()
      // "k", "l", and "m" cannot be accepted before respectively "a", "b", abd "c" are pulled
      for (s <- List("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m"))
        upstream.sendNext(s)
      upstream.sendComplete()
      downstream.request(20).expectNext("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")
      downstream.expectNoMessage(50.milliseconds).expectNext("k", "l", "m").expectComplete()
    }
  }

  "idleAlert()" should {

    "not trigger when upstream progresses fast enough" in new WithActorSystem {
      val result = Source.tick(0.second, 50.milliseconds, 1).via(idleAlert[Int](100.milliseconds, 100))
        .take(5).runWith(Sink.reduce[Int](_ + _))
      Await.result(result, 2.seconds) must be equalTo 5
    }

    "trigger exactly once per alert period" in new WithActorSystem {
      val result = Source.tick(0.second, 1.second, NotUsed).zip(Source.fromIterator(() => Iterator.from(0))).map(_._2)
        .via(idleAlert[Int](200.milliseconds, 100))
        .take(5).runWith(Sink.fold(List[Int]())(_ :+ _))
      Await.result(result, 5.seconds) must be equalTo List(0, 100, 1, 100, 2)
    }
  }

  "pairDifferent()" should {

    "return successive differences in pairs" in new WithActorSystem {
      val downstream = Source(List("one", "two", "two", "two", "three")).via(pairDifferent).runWith(TestSink.probe)
      downstream.request(3).expectNext(("one", "two"), ("two", "three")).expectComplete()
    }

    "work with an empty stream" in new WithActorSystem {
      val downstream = Source.empty[String].via(pairDifferent).runWith(TestSink.probe)
      downstream.request(1).expectComplete()
    }

    "return an empty stream for an one-element stream" in new WithActorSystem {
      val downstream = Source.single("one").via(pairDifferent).runWith(TestSink.probe)
      downstream.request(1).expectComplete()
    }

    "return an empty stream for a constant stream of elements" in new WithActorSystem {
      val downstream = Source.repeat("constant").take(5).via(pairDifferent).runWith(TestSink.probe)
      downstream.request(1).expectComplete()
    }

    "propagate an error" in new WithActorSystem {
      val (upstream, downstream) = TestSource.probe[Int].via(pairDifferent).toMat(TestSink.probe)(Keep.both).run()
      // downstream.request(1)
      upstream.sendNext(1).sendNext(2).sendNext(3)
      downstream.request(3).expectNext((1, 2), (2, 3))
      upstream.sendError(new RuntimeException)
      downstream.expectError()
    }
  }

  "onlyIncreasing" should {

    "filter out non-increasing elements" in new WithActorSystem {
      val downstream = Source(List(1, 2, 3, 0, 0, 4, -4, 5, 5, -5, 4, 3)).via(onlyIncreasing(strict = true)).runWith(TestSink.probe)
      downstream.request(6).expectNext(1, 2, 3, 4, 5).expectComplete()
    }

    "support specifying an alternate ordering" in new WithActorSystem {
      val ordering = new Ordering[Int] {
        override def compare(x: Int, y: Int) = implicitly[Ordering[Int]].compare(y, x)
      }
      val downstream = Source(List(1, 2, 3, 0, 0, 4, -4, 5, 5, -5, 4, 3)).via(onlyIncreasing(strict = true)(ordering)).runWith(TestSink.probe)
      downstream.request(5).expectNext(1, 0, -4, -5).expectComplete()
    }

    "keep duplicates in non-strict mode" in new WithActorSystem {
      val downstream = Source(List(1, 2, 3, 0, 0, 4, -4, 5, 5, -5, 4, 3)).via(onlyIncreasing(strict = false)).runWith(TestSink.probe)
      downstream.request(7).expectNext(1, 2, 3, 4, 5, 5).expectComplete()
    }

    "keep duplicates in non-strict mode with alternate ordering" in new WithActorSystem {
      val ordering = new Ordering[Int] {
        override def compare(x: Int, y: Int) = implicitly[Ordering[Int]].compare(y, x)
      }
      val downstream = Source(List(1, 2, 3, 0, 0, 4, -4, 5, 5, -5, 4, 3)).via(onlyIncreasing(strict = false)(ordering)).runWith(TestSink.probe)
      downstream.request(6).expectNext(1, 0, 0, -4, -5).expectComplete()
    }

    "transform an empty stream into an empty stream" in new WithActorSystem {
      val downstream = Source.empty[Int].via(onlyIncreasing(strict = true)).runWith(TestSink.probe)
      downstream.request(1).expectComplete()
    }

    "transform a one-element stream into a one-element stream" in new WithActorSystem {
      val downstream = Source.single(42).via(onlyIncreasing(strict = true)).runWith(TestSink.probe)
      downstream.request(2).expectNext(42).expectComplete()
    }
  }

}
