import dispatch._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import org.specs2.mutable._
import org.specs2.specification._

class DatabaseSpec extends DbSpecification {

  implicit val formats = DefaultFormats

  val dbSuffix = "databasetest"

  "db.insert()" should {

    "be able to insert a new document with an explicit id" in {
      http(db.insert("docid", JObject(Nil)))._1 must be equalTo("docid")
    }

    "be able to insert a new document with an explicit id in option" in {
      http(db.insert(JObject(Nil), Some("docid")))._1 must be equalTo("docid")
    }

    "be able to insert a new document with an implicit id" in {
      http(db.insert(JObject(Nil)))._1 must be matching("[0-9a-f]{32}")
    }

    "be able to insert a new document with an embedded id" in {
      http(db.insert(Map("_id" -> "docid")))._1 must be equalTo("docid")
    }

    "be able to update a document with an embedded id" in {
      http(db.insert(Map("_id" -> "docid")))._1 must be equalTo("docid")
      val updatedDoc = http(db("docid")) ~ ("foo" -> "bar")
      http(db.insert(updatedDoc))._1 must be equalTo("docid")
    }

    "return a consistent rev" in {
      http(db.insert("docid", JObject(Nil)))._2 must be matching("1-[0-9a-f]{32}")
    }

  }

  "db.apply()" should {

    "be able to retrieve the content of a document" in {
      val id = http(db.insert(Map("key" -> "value")))._1
      val doc = http(db(id))
      (doc \ "key").extract[String] must be equalTo("value")
    }

    "be able to retrieve an older revision of a document with two params" in {
      val (id, rev) = http(db.insert(Map("key" -> "value")))
      val doc = http(db(id))
      http(db.insert(doc ~ ("key" -> "newValue")))
      (http(db(id, rev)) \ "key").extract[String] must be equalTo("value")
    }

    "be able to retrieve an older revision of a document with a params map" in {
      val (id, rev) = http(db.insert(Map("key" -> "value")))
      val doc = http(db(id))
      http(db.insert(doc ~ ("key" -> "newValue")))
      (http(db(id, Map("rev" -> rev))) \ "key").extract[String] must be equalTo("value")
    }

    "be able to retrieve an older revision of a document with a params sequence" in {
      val (id, rev) = http(db.insert(Map("key" -> "value")))
      val doc = http(db(id))
      http(db.insert(doc ~ ("key" -> "newValue")))
      (http(db(id, Seq("rev" -> rev))) \ "key").extract[String] must be equalTo("value")
    }

  }

  "db.delete()" should {

    "be able to delete a document" in {
      val (id, rev) = http(db.insert(JObject(Nil)))
      http(db.delete(id, rev))
      http(db(id)) must throwA[StatusCode]
    }

    "fail when trying to delete a non-existing document" in {
      http(db.delete("foo", "bar")) must throwA[StatusCode]
    }

    "fail when trying to delete a deleted document" in {
      val (id, rev) = http(db.insert(JObject(Nil)))
      http(db.delete(id, rev))
      http(db.delete(id, rev)) must throwA[StatusCode]
    }

    "fail when trying to delete an older revision of a document" in {
      val (id, rev) = http(db.insert(Map("key" -> "value")))
      val doc = http(db(id))
      http(db.insert(doc ~ ("key" -> "newValue")))
      http(db.delete(id, rev)) must throwA[StatusCode]
    }
  }

  "db.bulkDocs" should {

    "be able to insert a single document" in {
      (http(db.bulkDocs(Seq(Map("_id" -> "docid")))).head \ "id").extract[String] must be equalTo("docid")
    }

    "fail to insert a duplicate document" in {
      http(db.bulkDocs(Seq(Map("_id" -> "docid"))))
      (http(db.bulkDocs(Seq(Map("_id" -> "docid", "extra" -> "other")))).head \ "error").extract[String] must be equalTo("conflict")
    }

    "fail to insert a duplicate document at once" in {
      (http(db.bulkDocs(Seq(Map("_id" -> "docid"),
			    Map("_id" -> "docid", "extra" -> "other"))))(1) \ "error").extract[String] must be equalTo("conflict")
    }

    "accept to insert a duplicate document in batch mode" in {
      (http(db.bulkDocs(Seq(Map("_id" -> "docid"),
			    Map("_id" -> "docid", "extra" -> "other")),
			true))(1) \ "id").extract[String] must be equalTo("docid")
    }

    "generate conflicts when inserting duplicate documents in batch mode" in {
      http(db.bulkDocs(Seq(Map("_id" -> "docid"),
			   Map("_id" -> "docid", "extra" -> "other"),
			   Map("_id" -> "docid", "extra" -> "yetAnother")),
		       true))
      (http(db("docid", Map("conflicts" -> "true"))) \ "_conflicts").extract[List[String]] must have size(2)
    }

  }

}
