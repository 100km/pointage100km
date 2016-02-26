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
      val source = Source.tick(0.second, 50.milliseconds, 1).zipWith(Source(1 to 100)) { case (_, n) => n }
      // This will get the samples at 0, 100, 150, and 200 milliseconds, namely 1, 3, 5, and 7
      val result = source.via(sample(70.milliseconds)).take(4).runWith(Sink.reduce[Int](_+_))
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

}
