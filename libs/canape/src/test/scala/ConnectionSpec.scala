import net.rfc1149.canape._
import org.specs2.mutable._
import org.specs2.specification._

class ConnectionSpec extends DbSpecification {

  val dbSuffix = "connectiontest"

  "couch.status()" should {

    "have a version we are comfortable with" in {
      couch.status().execute().version must startWith("1.")
    }

  }

  "couch.activeTasks()" should {

    "be queryable" in {
      couch.activeTasks().execute()
      success
    }

  }

  "db.delete()" should {

    "be able to delete an existing database" in {
      db.delete().execute()
      success
    }

    "fail when we trying to delete a non-existing database" in {
      db.delete().execute()
      db.delete().execute must throwA[Exception]
    }

  }

  "db.create()" should {

    "be able to create a non-existing database" in {
      db.delete().execute()
      db.create().execute()
      success
    }

    "fail when trying to create an existing database" in {
      db.create().execute must throwA[Exception]
    }

  }

}
