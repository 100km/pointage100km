import akka.http.scaladsl.model.HttpResponse
import net.rfc1149.canape.Couch
import net.rfc1149.canape.Couch.StatusError

class CouchSpec extends WithDbSpecification("couch") {

  "couch.status()" should {

    "have a version we are comfortable working with" in {
      waitForResult(couch.status()).version must beGreaterThan("1.6.0")
    }

    "fail properly if the HTTP server is not running" in {
      waitForResult(new Couch("localhost", 5985).status()) must throwA[RuntimeException]
    }

    "fail properly if the HTTPS server is not running" in {
      waitForResult(new Couch("localhost", 5985, secure = true).status()) must throwA[RuntimeException]
    }

  }

  "couch.activeTasks()" should {

    "be queryable" in {
      waitForResult(couch.activeTasks())
      success
    }

  }

  "couch.databases()" should {

    "contain the current database in the list" in new freshDb {
      waitForResult(couch.databases()) must contain(db.databaseName)
    }

  }

  "couch.getUUID*()" should {

    "return an UUID with the expected length" in {
      waitForResult(couch.getUUID) must have size 32
    }

    "return distinct UUIDs when called in succession" in {
      val uuid1 = waitForResult(couch.getUUID)
      val uuid2 = waitForResult(couch.getUUID)
      uuid1 must not be equalTo(uuid2)
    }

    "return distinct UUIDs when called in bulk mode" in {
      waitForResult(couch.getUUIDs(50)).distinct must have size 50
    }
  }

  "Couch.checkStatus()" should {

    "let the response go through if it is valid" in {
      Couch.checkStatus(waitForResult(couch.makeRawGetRequest("/"))) must beAnInstanceOf[HttpResponse]
    }

    "intercept the response with StatusError if it is not valid" in {
      Couch.checkStatus(waitForResult(couch.makeRawGetRequest("/non-existent"))) must throwA[StatusError]
    }
  }

}
