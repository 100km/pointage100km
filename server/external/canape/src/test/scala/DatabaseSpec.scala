import akka.http.scaladsl.model.HttpResponse
import net.rfc1149.canape.Couch.StatusError
import net.rfc1149.canape.Database.UpdateSequence
import net.rfc1149.canape._
import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

class DatabaseSpec extends WithDbSpecification("db") {

  private def insertedId(f: Future[JsValue]): Future[String] = f map { js ⇒ (js \ "id").as[String] }

  private def insertedRev(f: Future[JsValue]): Future[String] = f map { js ⇒ (js \ "rev").as[String] }

  private def inserted(f: Future[JsValue]): Future[(String, String)] =
    for (id ← insertedId(f); rev ← insertedRev(f)) yield (id, rev)

  private def insertPerson(db: Database, firstName: String, lastName: String, age: Int): Future[JsValue] =
    db.insert(Json.obj("firstName" → firstName, "lastName" → lastName, "age" → age, "type" → "person"))

  private def pendingUnlessAllOrNothing() = pendingIfNotCouchDB1("all_or_nothing parameter is not yet supported in CouchDB 2.0")

  private def installDesignAndDocs(db: Database) = {
    val upd =
      """
        | function(doc, req) {
        |   var newdoc = JSON.parse(req.form.json);
        |   newdoc._id = req.id;
        |   if (doc)
        |     newdoc._rev = doc._rev;
        |   return [newdoc, {
        |     headers : {
        |      "Content-Type" : "application/json"
        |      },
        |     body: JSON.stringify(newdoc)
        |   }];
        | };
      """.stripMargin
    val updError =
      """
        | function(doc, req) {
        |   return [null, { code: 400, body: "Check 400 answer" }];
        | }
      """.stripMargin
    val replace =
      """
        | function(doc, req) {
        |   var newdoc = JSON.parse(req.body);
        |   newdoc._id = req.id;
        |   if (doc)
        |     newdoc._rev = doc._rev;
        |   return [newdoc, {
        |     headers : {
        |      "Content-Type" : "application/json"
        |      },
        |     body: JSON.stringify(newdoc)
        |   }];
        | };
      """.stripMargin
    val personsMap =
      """
        | function(doc) {
        |   if (doc.type == "person") {
        |     emit(doc.firstName, { "name": doc.firstName, "age": doc.age });
        |     emit(doc.lastName, { "name": doc.lastName, "age": doc.age });
        |   }
        | }
      """.stripMargin
    val personsReduce =
      """
        | function(key, values, rereduce) {
        |   return Math.max.apply(Math, rereduce ? values : values.map(function(p) { return p.age; }));
        | }
      """.stripMargin
    val list =
      """
        | function(head, req) {
        |   var first = true
        |   while (row = getRow()) {
        |     if (!first) {
        |       send(",");
        |     }
        |     first = false;
        |     send(row.value);
        |   }
        | }
      """.stripMargin
    val common = Json.obj(
      "updates" → Json.obj("upd" → upd, "updError" → updError, "replace" → replace),
      "views" → Json.obj("persons" → Json.obj("map" → personsMap, "reduce" → personsReduce)),
      "lists" → Json.obj("list" → list))
    waitForResult(db.insert(common, "_design/common"))
    waitForResult(Future.sequence(for (
      (f, l, a) ← List(("Arthur", "Dent", 20), ("Zaphod", "Beeblebrox", 40),
        ("Buffy", "Summers", 23), ("Arthur", "Fubar", 27))
    ) yield insertPerson(db, f, l, a)))
  }

  "==" should {

    "tell that two equal databases are equal" in new freshDb {
      db must be equalTo db
    }

    "tell that two different databases are not equal" in new freshDb {
      db must not be equalTo(db.couch.db("non-existing"))
    }

    "tell that a database is different from another kind of object" in new freshDb {
      db must not be equalTo("foobar")
    }

    "get a distinctive hashCode" in new freshDb {
      db.hashCode must not be equalTo(db.couch.db("non-existing").hashCode)
    }
  }

  "db.delete() without argument" should {

    "be able to delete an existing database" in new freshDb {
      waitForResult(db.delete())
      success
    }

    "fail when we try to delete a non-existing database" in new freshDb {
      waitForResult(db.delete())
      waitForResult(db.delete()) must throwA[StatusError]
    }

  }

  "db.create()" should {

    "be able to create a non-existing database" in new freshDb {
      waitForResult(db.delete())
      waitForResult(db.create())
      success
    }

    "fail when trying to create an existing database" in new freshDb {
      waitForResult(db.create()) must throwA[StatusError]
    }

  }

  "db.status()" should {

    "return a meaningful object" in new freshDb {
      (waitForResult(db.status()) \ "db_name").as[String] must be equalTo db.databaseName
    }

  }

  "db.insert()" should {

    "be able to insert a new document with an explicit id" in new freshDb {
      waitForResult(insertedId(db.insert(JsObject(Nil), "docid"))) must be equalTo "docid"
    }

    "be able to insert a new document with an explicit non-ASCII id" in new freshDb {
      waitForResult(insertedId(db.insert(JsObject(Nil), "docéçà"))) must be equalTo "docéçà"
    }

    "be able to insert a new document with an explicit id in batch mode" in new freshDb {
      waitForResult(insertedId(db.insert(JsObject(Nil), "docid", batch = true))) must be equalTo "docid"
    }

    "be able to insert a new document with an implicit id" in new freshDb {
      val r = waitForResult(insertedId(db.insert(JsObject(Nil))))
      r must have size 32
      r must be matching "[0-9a-f]{32}"
    }

    "be able to insert a new document with an implicit id in batch mode" in new freshDb {
      val r = waitForResult(insertedId(db.insert(JsObject(Nil), batch = true)))
      r must have size 32
      r must be matching "[0-9a-f]{32}"
    }

    "be able to insert a new document with an embedded id" in new freshDb {
      waitForResult(insertedId(db.insert(Json.obj("_id" → "docid")))) must be equalTo "docid"
    }

    "be able to insert a new document with an embedded id in batch mode" in new freshDb {
      waitForResult(insertedId(db.insert(Json.obj("_id" → "docid"), batch = true))) must be equalTo "docid"
    }

    "be able to update a document with an embedded id" in new freshDb {
      waitForResult(insertedId(db.insert(Json.obj("_id" → "docid")))) must be equalTo "docid"
      val updatedDoc = waitForResult(db("docid")) ++ Json.obj("foo" → "bar")
      waitForResult(insertedId(db.insert(updatedDoc))) must be equalTo "docid"
    }

    "be able to update a document with an embedded id in batch mode" in new freshDb {
      waitForResult(insertedId(db.insert(Json.obj("_id" → "docid")))) must be equalTo "docid"
      val updatedDoc = waitForResult(db("docid")) ++ Json.obj("foo" → "bar")
      waitForResult(insertedId(db.insert(updatedDoc, batch = true))) must be equalTo "docid"
    }

    "return a consistent rev" in new freshDb {
      waitForResult(insertedRev(db.insert(JsObject(Nil), "docid"))) must be matching "1-[0-9a-f]{32}"
    }

    "fail properly if the document already exists" in new freshDb {
      waitForResult(db.insert(JsObject(Nil), "docid"))
      Try(waitForResult(db.insert(Json.obj("foo" → "bar"), "docid"))) match {
        case Failure(Couch.StatusError(409, _, _)) ⇒ success
        case Failure(t) ⇒ failure(s"unexpected exception thrown: $t")
        case Success(s) ⇒ failure(s"unexpected value returned: $s")
      }
    }

    "fail properly if the database is absent" in new freshDb {
      val newDb = db.couch.db("nonexistent-database")
      waitForResult(insertedRev(newDb.insert(JsObject(Nil)))) must throwA[StatusError]
    }

  }

  "db.apply()" should {

    "be able to retrieve the content of a document" in new freshDb {
      val id = waitForResult(insertedId(db.insert(Json.obj("key" → "value"))))
      val doc = waitForResult(db(id))
      (doc \ "key").as[String] must be equalTo "value"
    }

    "be able to retrieve an older revision of a document with two params" in new freshDb {
      val (id, rev) = waitForResult(inserted(db.insert(Json.obj("key" → "value"))))
      val doc = waitForResult(db(id))
      waitForResult(db.insert(doc ++ Json.obj("key" → "newValue")))
      (waitForResult(db(id, rev)) \ "key").as[String] must be equalTo "value"
    }

    "be able to retrieve an older revision of a document with a params map" in new freshDb {
      val (id, rev) = waitForResult(inserted(db.insert(Json.obj("key" → "value"))))
      val doc = waitForResult(db(id))
      waitForResult(db.insert(doc ++ Json.obj("key" → "newValue")))
      (waitForResult(db(id, Map("rev" → rev))) \ "key").as[String] must be equalTo "value"
    }

    "be able to retrieve an older revision of a document with a params sequence" in new freshDb {
      val (id, rev) = waitForResult(inserted(db.insert(Json.obj("key" → "value"))))
      val doc = waitForResult(db(id))
      waitForResult(db.insert(doc ++ Json.obj("key" → "newValue")))
      (waitForResult(db(id, Seq("rev" → rev))) \ "key").as[String] must be equalTo "value"
    }

    "be able to cope with documents containing non-ASCII characters" in new freshDb {
      private val s = "Épisode àçõœæéèêß"
      val id = waitForResult(insertedId(db.insert(Json.obj("data" → s))))
      (waitForResult(db(id)) \ "data").as[String] must be equalTo s
    }

  }

  "db.delete()" should {

    "be able to delete a document" in new freshDb {
      val (id, rev) = waitForResult(inserted(db.insert(JsObject(Nil))))
      waitForResult(db.delete(id, rev)) must startWith("2-")
      waitForResult(db(id)) must throwA[StatusError]
    }

    "be able to delete a document using it directly" in new freshDb {
      val (id, rev) = waitForResult(inserted(db.insert(JsObject(Nil))))
      val doc = waitForResult(db(id))
      waitForResult(db.delete(doc)) must startWith("2-")
    }

    "fail when trying to delete a non-existing document" in new freshDb {
      waitForResult(db.delete("foo", "bar")) must throwA[StatusError]
    }

    "fail when trying to delete a deleted document" in new freshDb {
      val (id, rev) = waitForResult(inserted(db.insert(JsObject(Nil))))
      waitForResult(db.delete(id, rev))
      waitForResult(db.delete(id, rev)) must throwA[StatusError]
    }

    "fail when trying to delete an older revision of a document" in new freshDb {
      val (id, rev) = waitForResult(inserted(db.insert(Json.obj("key" → "value"))))
      val doc = waitForResult(db(id))
      waitForResult(db.insert(doc ++ Json.obj("key" → "newValue")))
      waitForResult(db.delete(id, rev)) must throwA[StatusError]
    }

    "be able to bulk delete zero revisions of a non-existent document" in new freshDb {
      waitForResult(db.delete("docid", Seq())) must beEmpty
    }

    "be able to bulk delete one revision of a document" in new freshDb {
      val (id, rev) = waitForResult(inserted(db.insert(Json.obj())))
      waitForResult(db.delete(id, Seq(rev))) must be equalTo Seq(rev)
      waitForResult(db(id)) must throwA[StatusError]
    }

    "not throw an error when bulk delete a unique non-existent revision" in new freshDb {
      val (id, rev) = waitForResult(inserted(db.insert(Json.obj())))
      waitForResult(db.delete(id, Seq("1-1"))) must beEmpty
      (waitForResult(db(id)) \ "_rev").as[String] must be equalTo rev
    }

    "be able to bulk delete several revisions of a document" in new freshDb {
      val id = "docid"
      waitForEnd(
        db.insert(Json.obj("_rev" → "0-0"), id = id, newEdits = false),
        db.insert(Json.obj("_rev" → "1-1"), id = id, newEdits = false))
      waitForResult(db.delete(id, Seq("0-0", "1-1"))) must be equalTo Seq("0-0", "1-1")
      waitForResult(db(id)) must throwA[StatusError]
    }

    "only return succesfully bulk deleted revisions of a document" in new freshDb {
      val id = "docid"
      waitForEnd(
        db.insert(Json.obj("_rev" → "0-0"), id = id, newEdits = false),
        db.insert(Json.obj("_rev" → "1-1"), id = id, newEdits = false))
      waitForResult(db.delete(id, Seq("0-0", "2-2", "1-1"))) must be equalTo Seq("0-0", "1-1")
      waitForResult(db(id)) must throwA[StatusError]
    }

    "not delete untargeted revisions when bulk deleting" in new freshDb {
      val id = "docid"
      waitForEnd(
        db.insert(Json.obj("_rev" → "0-0"), id = id, newEdits = false),
        db.insert(Json.obj("_rev" → "1-1"), id = id, newEdits = false),
        db.insert(Json.obj("_rev" → "2-2"), id = id, newEdits = false))
      waitForResult(db.delete(id, Seq("0-0", "2-2"))) must be equalTo Seq("0-0", "2-2")
      (waitForResult(db(id)) \ "_rev").as[String] must be equalTo "1-1"
    }

    "be able to not fail at bulk deleting a unique non-existing revision of a document" in new freshDb {
      val (id, rev) = waitForResult(inserted(db.insert(Json.obj())))
      waitForResult(db.delete(id, Seq(rev.dropRight(8) + "00000000"))) must beEmpty
      (waitForResult(db(id)) \ "_rev").as[String] must be equalTo rev
    }

    "be able to not fail at bulk deleting a unique revision of a non-existing document" in new freshDb {
      val (id, rev) = waitForResult(inserted(db.insert(Json.obj())))
      waitForResult(db.delete("docid", Seq(rev.dropRight(8) + "00000000"))) must beEmpty
    }

    "be able to delete selected revisions of a document" in new freshDb {
      pendingUnlessAllOrNothing()
      val revs = waitForResult(db.bulkDocs(
        List("foo", "bar", "baz").map(v ⇒ Json.obj("_id" → "docid", "value" → v)),
        allOrNothing = true)).map(doc ⇒ (doc \ "rev").as[String])
      waitForResult(db.delete("docid", revs.drop(1))) must have size 2
      waitForResult(db.delete("docid", revs.take(1))) must have size 1
      waitForResult(db.delete("docid", revs.take(1))) must beEmpty
    }

    "be able to delete a document given only its id" in new freshDb {
      val id = waitForResult(insertedId(db.insert(Json.obj("key" → "value"))))
      waitForResult(db.delete(id)) must startWith("2-")
      waitForResult(db.delete(id)) must throwA[StatusError]
    }

    "be able to delete multiple revisions of a document given only its id" in new freshDb {
      pendingUnlessAllOrNothing()
      waitForResult(db.bulkDocs(
        List("foo", "bar", "baz").map(v ⇒ Json.obj("_id" → "docid", "value" → v)),
        allOrNothing = true))
      waitForResult(db.deleteAll("docid")) must have size 3
      waitForResult(db.deleteAll("docid")) must throwA[StatusError]
    }
  }

  "db.deleteAll" should {

    "be able to delete a document given only its id" in new freshDb {
      val (id, rev) = waitForResult(inserted(db.insert(Json.obj("key" → "value"))))
      waitForResult(db.deleteAll(id)) must be equalTo Seq(rev)
      waitForResult(db.deleteAll(id)) must throwA[StatusError]
    }
  }

  "db.bulkDocs" should {

    "be able to insert a single document" in new freshDb {
      (waitForResult(db.bulkDocs(Seq(Json.obj("_id" → "docid")))).head \ "id").as[String] must be equalTo "docid"
    }

    "fail to insert a duplicate document" in new freshDb {
      waitForResult(db.bulkDocs(Seq(Json.obj("_id" → "docid"))))
      (waitForResult(db.bulkDocs(Seq(Json.obj("_id" → "docid", "extra" → "other")))).head \ "error").as[String] must be equalTo "conflict"
    }

    "fail to insert a duplicate document at once" in new freshDb {
      (waitForResult(db.bulkDocs(Seq(
        Json.obj("_id" → "docid"),
        Json.obj("_id" → "docid", "extra" → "other"))))(1) \ "error").as[String] must be equalTo "conflict"
    }

    "accept to insert a duplicate document in batch mode" in new freshDb {
      pendingUnlessAllOrNothing()
      (waitForResult(db.bulkDocs(
        Seq(
          Json.obj("_id" → "docid"),
          Json.obj("_id" → "docid", "extra" → "other")),
        allOrNothing = true))(1) \ "id").as[String] must be equalTo "docid"
    }

    "reject allOrNothing queries in CouchDB 2.0" in new freshDb {
      if (isCouchDB1)
        skipped("not applicable to CouchDB 1.x")
      (waitForResult(db.bulkDocs(
        Seq(
          Json.obj("_id" → "docid"),
          Json.obj("_id" → "docid", "extra" → "other")),
        allOrNothing = true))(1) \ "id").as[String] must throwA[IllegalArgumentException]
    }

    "generate conflicts when inserting duplicate documents in batch mode" in new freshDb {
      pendingUnlessAllOrNothing()
      waitForResult(db.bulkDocs(
        Seq(
          Json.obj("_id" → "docid"),
          Json.obj("_id" → "docid", "extra" → "other"),
          Json.obj("_id" → "docid", "extra" → "yetAnother")),
        allOrNothing = true))
      (waitForResult(db("docid", Map("conflicts" → "true"))) \ "_conflicts").as[Array[JsValue]] must have size 2
    }

  }

  "db.allDocs" should {

    "return an empty count for an empty database" in new freshDb {
      waitForResult(db.allDocs()).total_rows must be equalTo 0
    }

    "return a correct count when an element has been inserted" in new freshDb {
      waitForResult(db.insert(Json.obj("key" → "value"), "docid"))
      waitForResult(db.allDocs()).total_rows must be equalTo 1
    }

    "return a correct id enumeration when an element has been inserted" in new freshDb {
      waitForResult(db.insert(Json.obj("key" → "value"), "docid"))
      waitForResult(db.allDocs()).ids must be equalTo List("docid")
    }

    "return a correct key enumeration when an element has been inserted" in new freshDb {
      waitForResult(db.insert(Json.obj("key" → "value"), "docid"))
      waitForResult(db.allDocs()).keys[String] must be equalTo List("docid")
    }

    "return a correct values enumeration when an element has been inserted" in new freshDb {
      waitForResult(db.insert(Json.obj("key" → "value"), "docid"))
      val rev = (waitForResult(db.allDocs()).values[JsValue].head \ "rev").as[String]
      rev must be matching "1-[0-9a-f]{32}"
    }

    "be convertible to an items triple" in new freshDb {
      waitForResult(db.insert(Json.obj("key" → "value"), "docid"))
      val (id: String, key: String, value: JsValue) = waitForResult(db.allDocs()).items[String, JsValue].head
      (value \ "rev").as[String] must be matching "1-[0-9a-f]{32}"
    }

    "be convertible to an items quartuple in include_docs mode" in new freshDb {
      waitForResult(db.insert(Json.obj("key" → "value"), "docid"))
      val (id: String, key: String, value: JsValue, doc: Some[JsValue]) =
        waitForResult(db.allDocs(Map("include_docs" → "true"))).itemsWithDoc[String, JsValue, JsValue].head
      ((value \ "rev").as[String] must be matching "1-[0-9a-f]{32}") &&
        ((value \ "rev") must be equalTo (doc.get \ "_rev")) &&
        ((doc.get \ "key").as[String] must be equalTo "value")
    }

    "not return full docs in default mode" in new freshDb {
      waitForResult(db.insert(Json.obj("key" → "value"), "docid"))
      waitForResult(db.allDocs()).docs[JsValue] must be equalTo Nil
    }

    "return full docs in include_docs mode" in new freshDb {
      waitForResult(db.insert(Json.obj("key" → "value")))
      (waitForResult(db.allDocs(Map("include_docs" → "true"))).docs[JsValue].head \ "key").as[String] must be equalTo "value"
    }

    "return full docs in include_docs mode and option" in new freshDb {
      waitForResult(db.insert(Json.obj("key" → "value"), "docid"))
      (waitForResult(db.allDocs(Map("include_docs" → "true"))).docs[JsValue].head \ "key").as[String] must be equalTo "value"
    }

    "return an update sequence when asked to do so" in new freshDb {
      waitForResult(db.allDocs(Map("update_seq" → "true"))).update_seq must beSome[UpdateSequence]
    }

    "return no update sequence by default" in new freshDb {
      waitForResult(db.allDocs()).update_seq must beNone
    }

  }

  "db.compact()" should {
    "return with success" in new freshDb {
      (waitForResult(db.compact()) \ "ok").as[Boolean] must beTrue
    }
  }

  "db.ensureFullCommit()" should {
    "return with success" in new freshDb {
      (waitForResult(db.ensureFullCommit()) \ "ok").as[Boolean] must beTrue
    }
  }

  "db.changes()" should {

    "represent an empty set" in new freshDb {
      (waitForResult(db.changes()) \ "results").as[List[JsObject]] must beEmpty
    }

    "contain the only change" in new freshDb {
      val id = waitForResult(insertedId(db.insert(Json.obj())))
      ((waitForResult(db.changes()) \ "results").as[List[JsObject]].head \ "id").as[String] must be equalTo id
    }

    "return the change in long-polling state" in new freshDb {
      val changes = db.changes(Map("feed" → "longpoll"))
      changes.isCompleted must beFalse
      val id = waitForResult(insertedId(db.insert(Json.obj())))
      ((waitForResult(changes) \ "results").as[List[JsObject]].head \ "id").as[String] must be equalTo id
    }
  }

  "db.revs_limit()" should {
    "be settable and queryable" in new freshDb {
      waitForResult(db.revs_limit(1938))
      waitForResult(db.revs_limit()) must be equalTo 1938
    }
  }

  "db.update*()" should {

    "encode values" in new freshDb {
      installDesignAndDocs(db)
      val newDoc = waitForResult(db.updateForm("common", "upd", "docid", Map("json" → Json.stringify(Json.obj("foo" → "bar"))), keepBody = true)
        .flatMap(Couch.checkResponse[JsObject]))
      (newDoc \ "_id").as[String] must be equalTo "docid"
      (newDoc \ "_rev").toOption must beNone
      (newDoc \ "foo").as[String] must be equalTo "bar"
    }

    "encode values with non-ASCII characters" in new freshDb {
      installDesignAndDocs(db)
      val newDoc = waitForResult(db.updateForm("common", "upd", "docid", Map("json" → Json.stringify(Json.obj("foo" → "barré"))), keepBody = true)
        .flatMap(Couch.checkResponse[JsObject]))
      (newDoc \ "_id").as[String] must be equalTo "docid"
      (newDoc \ "_rev").toOption must beNone
      (newDoc \ "foo").as[String] must be equalTo "barré"
    }

    "insert documents" in new freshDb {
      installDesignAndDocs(db)
      waitForResult(db.updateForm("common", "upd", "docid", Map("json" → Json.stringify(Json.obj("foo" → "bar")))))
      val newDoc = waitForResult(db("docid"))
      (newDoc \ "_id").as[String] must be equalTo "docid"
      (newDoc \ "_rev").as[String] must startWith("1-")
      (newDoc \ "foo").as[String] must be equalTo "bar"
    }

    "insert documents with non-ASCII characters in id" in new freshDb {
      installDesignAndDocs(db)
      waitForResult(db.updateForm("common", "upd", "docidé", Map("json" → Json.stringify(Json.obj("foo" → "bar")))))
      val newDoc = waitForResult(db("docidé"))
      (newDoc \ "_id").as[String] must be equalTo "docidé"
      (newDoc \ "_rev").as[String] must startWith("1-")
      (newDoc \ "foo").as[String] must be equalTo "bar"
    }

    "update documents" in new freshDb {
      installDesignAndDocs(db)
      waitForResult(db.updateForm("common", "upd", "docid", Map("json" → Json.stringify(Json.obj("foo" → "bar")))))
      waitForResult(db.updateForm("common", "upd", "docid", Map("json" → Json.stringify(Json.obj("foo2" → "bar2")))))
      val updatedDoc = waitForResult(db("docid"))
      (updatedDoc \ "_id").as[String] must be equalTo "docid"
      (updatedDoc \ "_rev").as[String] must startWith("2-")
      (updatedDoc \ "foo").toOption must beNone
      (updatedDoc \ "foo2").as[String] must be equalTo "bar2"
    }

    "propagate errors from the update function" in new freshDb {
      installDesignAndDocs(db)
      val response = waitForResult(db.updateForm("common", "updError", "docid", Map[String, String]()))
      response.status.intValue() must be equalTo 400
    }

    "accept JSON documents with PUT" in new freshDb {
      installDesignAndDocs(db)
      waitForResult(db.updateBody("common", "replace", "docid", Json.obj("foo" → "bar")))
      val updatedDoc = waitForResult(db("docid"))
      (updatedDoc \ "foo").as[String] must be equalTo "bar"
    }

    "replace JSON documents with PUT" in new freshDb {
      installDesignAndDocs(db)
      waitForResult(db.updateBody("common", "replace", "docid", Json.obj("foo" → "bar")))
      val updatedDoc = waitForResult(db("docid"))
      (updatedDoc \ "foo").as[String] must be equalTo "bar"
      waitForResult(db.updateBody("common", "replace", "docid", Json.obj("foo" → "baz")))
      val updatedDoc2 = waitForResult(db("docid"))
      (updatedDoc2 \ "foo").as[String] must be equalTo "baz"
    }
  }

  "db.mapOnly()" should {

    "return correct values" in new freshDb {
      installDesignAndDocs(db)
      val result = waitForResult(db.mapOnly("common", "persons"))
      result.total_rows must be equalTo 8
    }
  }

  "db.view()" should {

    "return correct values when not grouping" in new freshDb {
      installDesignAndDocs(db)
      val result = waitForResult(db.view[JsValue, Int]("common", "persons"))
      result.size must be equalTo 1
      result.head._1 must be equalTo JsNull
      result.head._2 must be equalTo 40
    }

    "return correct values when grouping" in new freshDb {
      installDesignAndDocs(db)
      val result = waitForResult(db.view[String, Int]("common", "persons", Seq("group" → "true"))).toMap
      result.size must be equalTo 7
      result.keys must containAllOf(List("Arthur", "Beeblebrox", "Buffy", "Dent", "Fubar", "Summers", "Zaphod"))
      result.get("Arthur") must be equalTo Some(27)
    }

    "work as db.mapOnly when explicitely not reducing" in new freshDb {
      installDesignAndDocs(db)
      val result = waitForResult(db.view[JsValue, JsValue]("common", "persons", Seq("reduce" → "false")))
      result.size must be equalTo 8
    }
  }

  "db.viewWithUpdateSeq()" should {

    "see the same sequence number when no changes happened" in new freshDb {
      installDesignAndDocs(db)
      val (seq1, _) = waitForResult(db.viewWithUpdateSeq[JsValue, Int]("common", "persons"))
      val (seq2, _) = waitForResult(db.viewWithUpdateSeq[JsValue, Int]("common", "persons"))
      seq2.toLong must be equalTo seq1.toLong
    }

    "see the same sequence number when a non-matching document has been inserted" in new freshDb {
      installDesignAndDocs(db)
      val (seq1, _) = waitForResult(db.viewWithUpdateSeq[JsValue, Int]("common", "persons"))
      waitForEnd(db.insert(Json.obj()))
      val (seq2, _) = waitForResult(db.viewWithUpdateSeq[JsValue, Int]("common", "persons"))
      seq2.toLong must be equalTo seq1.toLong
    }

    "see an increased sequence number when a matching document has been inserted" in new freshDb {
      installDesignAndDocs(db)
      val (seq1, _) = waitForResult(db.viewWithUpdateSeq[JsValue, Int]("common", "persons"))
      waitForEnd(insertPerson(db, "Dawn", "Summers", 15))
      val (seq2, _) = waitForResult(db.viewWithUpdateSeq[JsValue, Int]("common", "persons"))
      seq2.toLong must be greaterThan seq1.toLong
    }

    "see the same stale sequence number when a matching document has been inserted" in new freshDb {
      installDesignAndDocs(db)
      val (seq1, _) = waitForResult(db.viewWithUpdateSeq[JsValue, Int]("common", "persons"))
      waitForEnd(insertPerson(db, "Dawn", "Summers", 15))
      val (seq2, _) = waitForResult(db.viewWithUpdateSeq[JsValue, Int]("common", "persons", Seq("stale" → "ok")))
      seq2.toLong must be equalTo seq1.toLong
    }

    "see an increased sequence number when a matching document has been inserted in update_after mode" in new freshDb {
      installDesignAndDocs(db)
      val (seq1, _) = waitForResult(db.viewWithUpdateSeq[JsValue, Int]("common", "persons"))
      waitForEnd(insertPerson(db, "Dawn", "Summers", 15))
      val (seq2, _) = waitForResult(db.viewWithUpdateSeq[JsValue, Int]("common", "persons", Seq("stale" → "update_after")))
      seq2.toLong must be equalTo seq1.toLong
      val (seq3, _) = waitForResult(db.viewWithUpdateSeq[JsValue, Int]("common", "persons"))
      seq3.toLong must be greaterThan seq2.toLong
    }
  }

  "db.list()" should {

    def responseToString(response: HttpResponse): Future[String] =
      response.entity.toStrict(FiniteDuration(1, SECONDS)).map(s ⇒ new String(s.data.toArray, "UTF-8"))

    "return correct values when not grouping" in new freshDb {
      installDesignAndDocs(db)
      val result = waitForResult(db.list("common", "list", "persons", keepBody = true).flatMap(responseToString))
      result must be equalTo "40"
    }

    "return correct values when grouping" in new freshDb {
      installDesignAndDocs(db)
      val result = waitForResult(db.list("common", "list", "persons", Seq("group" → "true"), keepBody = true).flatMap(responseToString)).split(',').map(_.toInt).sorted
      result must be equalTo Array(20, 23, 23, 27, 27, 40, 40)
    }
  }

  "db.latestRev()" should {

    "return the latest revision of a document" in new freshDb {
      val id = waitForResult(insertedId(db.insert(Json.obj())))
      private val rev = waitForResult(db.latestRev(id))
      rev must startWith("1-")
      rev must have length 34
    }

    "fail when the document does not exist" in new freshDb {
      waitForResult(db.latestRev("non-existing")) must throwA[StatusError]
    }
  }

  "db.replicateFrom()" should {

    "copy documents from the remote database" in new freshDb {
      val outer = db
      val (outerId, outerRev) = waitForResult(inserted(outer.insert(Json.obj())))
      new freshDb {
        waitForResult(db.replicateFrom(outer))
        val doc = waitForResult(db(outerId))
        (doc \ "_rev").as[String] must be equalTo outerRev
        db.delete()
      }
    }
  }

  "db.replicateTo()" should {

    "copy documents to the remote database" in new freshDb {
      val outer = db
      new freshDb {
        val (innerId, innerRev) = waitForResult(inserted(db.insert(Json.obj())))
        waitForResult(db.replicateTo(outer))
        val doc = waitForResult(outer(innerId))
        (doc \ "_rev").as[String] must be equalTo innerRev
        db.delete()
      }
    }
  }

}
