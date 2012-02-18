import net.liftweb.json._
import net.rfc1149.canape._

class DatabaseSpec extends DbSpecification {

  import implicits._

  val dbSuffix = "databasetest"

  private def insertedId(js: JValue) = (js \ "id").extract[String]

  private def insertedRev(js: JValue) = (js \ "rev").extract[String]

  private def inserted(js: JValue) = (insertedId(js), insertedRev(js))

  "db.insert()" should {

    "be able to insert a new document with an explicit id" in {
      insertedId(db.insert("docid", JObject(Nil)).execute()) must be equalTo("docid")
    }

    "be able to insert a new document with an explicit id in option" in {
      insertedId(db.insert(JObject(Nil), Some("docid")).execute()) must be equalTo("docid")
    }

    "be able to insert a new document with an implicit id" in {
      insertedId(db.insert(JObject(Nil)).execute()) must be matching("[0-9a-f]{32}")
    }

    "be able to insert a new document with an embedded id" in {
      insertedId(db.insert(Map("_id" -> "docid")).execute()) must be equalTo("docid")
    }

    "be able to update a document with an embedded id" in {
      insertedId(db.insert(Map("_id" -> "docid")).execute()) must be equalTo("docid")
      val updatedDoc = db("docid").execute() + ("foo" -> "bar")
      insertedId(db.insert(updatedDoc).execute()) must be equalTo("docid")
    }

    "return a consistent rev" in {
      insertedRev(db.insert("docid", JObject(Nil)).execute()) must be matching("1-[0-9a-f]{32}")
    }

  }

  "db.apply()" should {

    "be able to retrieve the content of a document" in {
      val id = insertedId(db.insert(Map("key" -> "value")).execute())
      val doc = db(id).execute()
      doc("key").extract[String] must be equalTo("value")
    }

    "be able to retrieve an older revision of a document with two params" in {
      val (id, rev) = inserted(db.insert(Map("key" -> "value")).execute())
      val doc = db(id).execute()
      db.insert(doc + ("key" -> "newValue")).execute()
      db(id, rev).execute()("key").extract[String] must be equalTo("value")
    }

    "be able to retrieve an older revision of a document with a params map" in {
      val (id, rev) = inserted(db.insert(Map("key" -> "value")).execute())
      val doc = db(id).execute()
      db.insert(doc + ("key" -> "newValue")).execute()
      (db(id, Map("rev" -> rev)).execute() \ "key").extract[String] must be equalTo("value")
    }

    "be able to retrieve an older revision of a document with a params sequence" in {
      val (id, rev) = inserted(db.insert(Map("key" -> "value")).execute())
      val doc = db(id).execute()
      db.insert(doc + ("key" -> "newValue")).execute()
      (db(id, Seq("rev" -> rev)).execute() \ "key").extract[String] must be equalTo("value")
    }

  }

  "db.delete()" should {

    "be able to delete a document" in {
      val (id, rev) = inserted(db.insert(JObject(Nil)).execute())
      db.delete(id, rev).execute()
      db(id).execute() must throwA[Exception]
    }

    "fail when trying to delete a non-existing document" in {
      db.delete("foo", "bar").execute() must throwA[Exception]
    }

    "fail when trying to delete a deleted document" in {
      val (id, rev) = inserted(db.insert(JObject(Nil)).execute())
      db.delete(id, rev).execute()
      db.delete(id, rev).execute() must throwA[Exception]
    }

    "fail when trying to delete an older revision of a document" in {
      val (id, rev) = inserted(db.insert(Map("key" -> "value")).execute())
      val doc = db(id).execute()
      db.insert(doc + ("key" -> "newValue")).execute()
      db.delete(id, rev).execute() must throwA[Exception]
    }
  }

  "db.bulkDocs" should {

    "be able to insert a single document" in {
      (db.bulkDocs(Seq(Map("_id" -> "docid"))).execute()(0) \ "id").extract[String] must be equalTo("docid")
    }

    "fail to insert a duplicate document" in {
      db.bulkDocs(Seq(Map("_id" -> "docid"))).execute()
      (db.bulkDocs(Seq(Map("_id" -> "docid", "extra" -> "other"))).execute()(0) \ "error").extract[String] must be equalTo("conflict")
    }

    "fail to insert a duplicate document at once" in {
      (db.bulkDocs(Seq(Map("_id" -> "docid"),
		       Map("_id" -> "docid", "extra" -> "other"))).execute()(1) \ "error").extract[String] must be equalTo("conflict")
    }

    "accept to insert a duplicate document in batch mode" in {
      (db.bulkDocs(Seq(Map("_id" -> "docid"),
		       Map("_id" -> "docid", "extra" -> "other")),
		   true).execute()(1) \ "id").extract[String] must be equalTo("docid")
    }

    "generate conflicts when inserting duplicate documents in batch mode" in {
      db.bulkDocs(Seq(Map("_id" -> "docid"),
        Map("_id" -> "docid", "extra" -> "other"),
        Map("_id" -> "docid", "extra" -> "yetAnother")),
        true).execute()
      (db("docid", Map("conflicts" -> "true")).execute() \ "_conflicts").children must have size(2)
    }

  }

  "db.allDocs" should {

    "return an empty count for an empty database" in {
      db.allDocs().execute().total_rows must be equalTo(0)
    }

    "return a correct count when an element has been inserted" in {
      db.insert("docid", Map("key" -> "value")).execute()
      db.allDocs().execute().total_rows must be equalTo(1)
    }

    "return a correct id enumeration when an element has been inserted" in {
      db.insert("docid", Map("key" -> "value")).execute()
      db.allDocs().execute().ids must be equalTo (List("docid"))
    }

    "return a correct key enumeration when an element has been inserted" in {
      db.insert("docid", Map("key" -> "value")).execute()
      db.allDocs().execute().keys[String] must be equalTo (List("docid"))
    }

    "return a correct values enumeration when an element has been inserted" in {
      db.insert("docid", Map("key" -> "value")).execute()
      val JString(rev) = db.allDocs().execute().values[JValue].head \ "rev"
      rev must be matching ("1-[0-9a-f]{32}")
    }

    "be convertible to an items triple" in {
      db.insert("docid", Map("key" -> "value")).execute()
      val (id: String, key: String, value: JValue) = db.allDocs().execute().items[String, JValue].head
      (value \ "rev").extract[String] must be matching ("1-[0-9a-f]{32}")
    }

    "be convertible to an items quartuple in include_docs mode" in {
      db.insert("docid", Map("key" -> "value")).execute()
      val (id: String, key: String, value: JValue, doc: JValue) =
	db.allDocs(Map("include_docs" -> "true")).execute().itemsWithDoc[String, JValue, JValue].head
      ((value \ "rev").extract[String] must be matching ("1-[0-9a-f]{32}")) &&
      ((value \ "rev") must be equalTo(doc \ "_rev")) &&
      ((doc \ "key").extract[String] must be equalTo("value"))
    }

    "not return full docs in default mode" in {
      db.insert("docid", Map("key" -> "value")).execute()
      db.allDocs().execute().docsOption[JValue] must be equalTo(List(None))
    }

    "return full docs in include_docs mode" in {
      db.insert("docid", Map("key" -> "value")).execute()
      db.allDocs(Map("include_docs" -> "true")).execute().docs[JValue].head \ "key" must
        be equalTo(JString("value"))
    }

    "return full docs in include_docs mode and option" in {
      db.insert("docid", Map("key" -> "value")).execute()
      db.allDocs(Map("include_docs" -> "true")).execute().docsOption[JValue].head.map(_ \ "key") must
        be equalTo(Some(JString("value")))
    }

  }

  "db.compact()" should {
    "return with success" in {
      db.compact().execute() \ "ok" must be equalTo(JBool(true))
    }
  }

  "db.ensureFullCommit()" should {
    "return with success" in {
      db.ensureFullCommit().execute() \ "ok" must be equalTo(JBool(true))
    }
  }

}
