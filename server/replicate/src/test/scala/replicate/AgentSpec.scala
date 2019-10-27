package replicate

import org.specs2.mutable._
import replicate.utils.Agent

import scala.concurrent.Await
import scala.concurrent.duration._

class AgentSpec extends Specification {

  "An Agent" should {

    "give back its initial value" in {
      val a = Agent(42)
      Await.result(a.future(), 1.second) must be equalTo 42
    }

    "give back a future to its initial value" in {
      val a = Agent(42)
      Await.result(a.future(), 1.second) must be equalTo 42
    }

    "be able to alter its value using a function" in {
      val a = Agent(42)
      a.alter(_ * 2)
      Await.result(a.future(), 1.second) must be equalTo 84
    }

    "be able to alter its value using a new value" in {
      val a = Agent(42)
      a.alter(43)
      Await.result(a.future(), 1.second) must be equalTo 43
    }

    "alter its value in order" in {
      val a = Agent(42)
      for (i <- 2 to 10) {
        a.alter(_ * i)
        a.alter(_ + 1)
      }
      Await.result(a.future(), 1.second) must be equalTo 155016101
    }

  }

}
