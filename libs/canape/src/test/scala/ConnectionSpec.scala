import dispatch._
import net.rfc1149.canape._
import org.specs2.mutable._
import org.specs2.specification._

class ConnectionSpec extends DbSpecification {

  val dbSuffix = "connectiontest"

  "couch.status()" should {

    "have a version we are comfortable with" in {
      http(couch.status).version must startWith("1.")
    }

  }

  "couch.activeTasks()" should {

    "return an empty list of tasks" in {
      http(couch.activeTasks) must beEmpty
    }

  }

  "db.delete()" should {

    "be able to delete an existing database" in {
      http(db.delete())
      success
    }

    "fail when we trying to delete a non-existing database" in {
      http(db.delete())
      http(db.delete()) must throwA[StatusCode]
    }

  }

  "db.create()" should {

    "be able to create a non-existing database" in {
      http(db.delete())
      http(db.create())
      success
    }

    "fail when trying to create an existing database" in {
      http(db.create()) must throwA[StatusCode]
    }

  }

}
