import org.specs2.mutable._
import play.api.libs.json.Json

class MangoSpec extends WithDbSpecification("mango") {

  private def skipIfCouchDB1() = if (isCouchDB1) skipped("introduced in CouchDB 2.0")

  "db.find()" should {

    "return an empty set when no document match" in new freshDb {
      skipIfCouchDB1()
      waitForEnd(db.insert(Json.obj("name" -> "Doe")))
      waitForResult(db.find(Json.obj("selector" -> Json.obj("name" -> "Plank")))) should be empty
    }

    "return matching documents" in new freshDb {
      skipIfCouchDB1()
      waitForEnd(
        db.insert(Json.obj("name" -> "Doe", "firstName" -> "John")),
        db.insert(Json.obj("name" -> "Doe", "firstName" -> "Joan")),
        db.insert(Json.obj("name" -> "Summers", "firstName" -> "Buffy")))
      waitForResult(db.find(Json.obj("selector" -> Json.obj("name" -> "Doe"), "fields" -> List("firstName"))))
        .map(js => (js \ "firstName").as[String]).sorted should be equalTo List("Joan", "John")
    }

  }

  "db.index()" should {

    "sort indexed documents" in new freshDb {
      skipIfCouchDB1()
      waitForEnd(
        db.insert(Json.obj("name" -> "Doe", "firstName" -> "John")),
        db.insert(Json.obj("name" -> "Doe", "firstName" -> "Joan")),
        db.insert(Json.obj("name" -> "Summers", "firstName" -> "Buffy")),
        db.index(Json.obj("index" -> Json.obj("fields" -> List("name", "firstName")))))
      waitForResult(db.find(Json.obj("selector" -> Json.obj("name" -> "Doe"), "sort" -> List("name", "firstName"), "fields" -> List("firstName"))))
        .map(js => (js \ "firstName").as[String]) should be equalTo List("Joan", "John")
      waitForResult(db.find(Json.obj("selector" -> Json.obj("name" -> "Doe"), "sort" -> List(Json.obj("name" -> "desc"), Json.obj("firstName" -> "desc")), "fields" -> List("firstName"))))
        .map(js => (js \ "firstName").as[String]) should be equalTo List("John", "Joan")
    }
  }

  "db.explain()" should {

    "return an explanation" in new freshDb {
      skipIfCouchDB1()
      waitForEnd(
        db.insert(Json.obj("name" -> "Doe", "firstName" -> "John")),
        db.insert(Json.obj("name" -> "Doe", "firstName" -> "Joan")),
        db.insert(Json.obj("name" -> "Summers", "firstName" -> "Buffy")),
        db.index(Json.obj("index" -> Json.obj("fields" -> List("name", "firstName")))))
      waitForResult(db.explain(Json.obj("selector" -> Json.obj("name" -> "Doe"), "sort" -> List("name", "firstName"), "fields" -> List("firstName")))).keys must contain("index")
    }
  }

}
