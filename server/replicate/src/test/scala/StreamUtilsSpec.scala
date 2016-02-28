import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.specs2.mutable._
import replicate.utils.StreamUtils._

import scala.concurrent.Await
import scala.concurrent.duration._

class StreamUtilsSpec extends Specification {

  trait withActorSystem extends After {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer.create(system)

    override def after() = system.terminate()
  }

  "sample()" should {

    "filter the right number of elements" in new withActorSystem {
      val source = Source.tick(0.second, 100.milliseconds, 1).zipWith(Source(1 to 100)) { case (_, n) => n }
      // This will get the samples at 0, 200, 300, and 040 milliseconds, namely 1, 3, 5, and 7
      val result = source.via(sample(140.milliseconds)).take(4).runWith(Sink.reduce[Int](_+_))
      Await.result(result, 1.second) must be equalTo 16
    }

    "propagate an upstream completion without delay" in new withActorSystem {
      val result = Source.single(1).via(sample(1.second)).runWith(Sink.reduce[Int](_+_))
      Await.result(result, 100.milliseconds) must be equalTo 1
    }

    "propagate an upstream error without delay" in new withActorSystem {
      val source = Source.single(1) ++ Source.failed(new RuntimeException("marker"))
      val result = source.via(sample(1.second)).runWith(Sink.reduce[Int](_+_))
      Await.result(result, 100.milliseconds) must throwA[RuntimeException]("marker")
    }

    "propagate an initial error" in new withActorSystem {
      val result = Source.failed[Int](new RuntimeException("marker")).via(sample(1.second)).runWith(Sink.head)
      Await.result(result, 2.seconds) must throwA[RuntimeException]("marker")
    }

  }

  "idleAlert()" should {

    "not trigger when upstream progresses fast enough" in new withActorSystem {
      val result = Source.tick(0.second, 50.milliseconds, 1).via(idleAlert[Int](100.milliseconds, 100))
        .take(5).runWith(Sink.reduce[Int](_+_))
      Await.result(result, 2.seconds) must be equalTo 5
    }

    "trigger exactly once per alert period" in new withActorSystem {
      val result = Source.tick(0.second, 50.milliseconds, 1).via(idleAlert[Int](10.milliseconds, 100))
        .take(5).runWith(Sink.fold(List[Int]())(_ :+ _))
      Await.result(result, 2.seconds) must be equalTo List(1, 100, 1, 100, 1)
    }
  }

  "pairDifferent()" should {

    "return successive differences in pairs" in new withActorSystem {
      val result = Source(List("one", "two", "two", "two", "three")).via(pairDifferent).runFold[List[(String, String)]](Nil)(_ :+ _)
      Await.result(result, 2.seconds) must be equalTo List(("one", "two"), ("two", "three"))
    }

    "work with an empty stream" in new withActorSystem {
      val result = Source.empty[String].via(pairDifferent).runFold[List[(String, String)]](Nil)(_ :+ _)
      Await.result(result, 2.seconds) must be equalTo Nil
    }

    "return an empty stream for an one-element stream" in new withActorSystem {
      val result = Source.single("one").via(pairDifferent).runFold[List[(String, String)]](Nil)(_ :+ _)
      Await.result(result, 2.seconds) must be equalTo Nil
    }

    "return an empty stream for a constant stream of elements" in new withActorSystem {
      val result = Source.repeat("constant").take(5).via(pairDifferent).runFold[List[(String, String)]](Nil)(_ :+ _)
      Await.result(result, 2.seconds) must be equalTo Nil
    }
  }

  "onlyIncreasing" should {

    "filter out non-increasing elements" in new withActorSystem {
      val result = Source(List(1, 2, 3, 0, 0, 4, -4, 5, 5, -5, 4, 3)).via(onlyIncreasing(strict = true)).runFold[List[Int]](Nil)(_ :+ _)
      Await.result(result, 2.seconds) must be equalTo List(1, 2, 3, 4, 5)
    }

    "support specifying an alternate ordering" in new withActorSystem {
      val ordering = new Ordering[Int] {
        override def compare(x: Int, y: Int) = implicitly[Ordering[Int]].compare(y, x)
      }
      val result = Source(List(1, 2, 3, 0, 0, 4, -4, 5, 5, -5, 4, 3)).via(onlyIncreasing(strict = true)(ordering)).runFold[List[Int]](Nil)(_ :+ _)
      Await.result(result, 2.seconds) must be equalTo List(1, 0, -4, -5)
    }

    "keep duplicates in non-strict mode" in new withActorSystem {
      val result = Source(List(1, 2, 3, 0, 0, 4, -4, 5, 5, -5, 4, 3)).via(onlyIncreasing(strict = false)).runFold[List[Int]](Nil)(_ :+ _)
      Await.result(result, 2.seconds) must be equalTo List(1, 2, 3, 4, 5, 5)
    }

    "keep duplicates in non-strict mode with alternate ordering" in new withActorSystem {
      val ordering = new Ordering[Int] {
        override def compare(x: Int, y: Int) = implicitly[Ordering[Int]].compare(y, x)
      }
      val result = Source(List(1, 2, 3, 0, 0, 4, -4, 5, 5, -5, 4, 3)).via(onlyIncreasing(strict = false)(ordering)).runFold[List[Int]](Nil)(_ :+ _)
      Await.result(result, 2.seconds) must be equalTo List(1, 0, 0, -4, -5)
    }

    "transform an empty stream into an empty stream" in new withActorSystem {
      val result = Source.empty[Int].via(onlyIncreasing(strict = true)).runFold[List[Int]](Nil)(_ :+ _)
      Await.result(result, 2.seconds) must be equalTo Nil
    }

    "transform a one-element stream into a one-element stream" in new withActorSystem {
      val result = Source.single(42).via(onlyIncreasing(strict = true)).runFold[List[Int]](Nil)(_ :+ _)
      Await.result(result, 2.seconds) must be equalTo List(42)
    }
  }

}
