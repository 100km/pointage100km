import java.util.concurrent.TimeoutException

import akka.Done
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.testkit.scaladsl.TestSink
import net.rfc1149.canape.Couch.StatusError
import net.rfc1149.canape._
import play.api.libs.json._

import scala.concurrent.Await
import scala.concurrent.duration._

class ContinuousChangesSpec extends WithDbSpecification("db") {

  "db.continuousChanges()" should {

    "see the creation of new documents" in new freshDb {
      val changes = db.continuousChanges()
      val result = changes.via(Database.onlySeq).map(j ⇒ (j \ "id").as[String]).take(3).runFold[List[String]](Nil)(_ :+ _)
      waitEventually(db.insert(JsObject(Nil), "docid1"), db.insert(JsObject(Nil), "docid2"), db.insert(JsObject(Nil), "docid3"))
      waitForResult(result).sorted must be equalTo List("docid1", "docid2", "docid3")
    }

    "see the creation of new documents as soon as they are created" in new freshDb {
      val changes = db.continuousChanges()
      val downstream = changes.map(j ⇒ (j \ "id").as[String]).take(3).runWith(TestSink.probe)
      waitEventually(db.insert(JsObject(Nil), "docid1"))
      downstream.requestNext("docid1")
      waitEventually(db.insert(JsObject(Nil), "docid2"))
      downstream.requestNext("docid2")
      waitEventually(db.insert(JsObject(Nil), "docid3"))
      downstream.requestNext("docid3")
      downstream.request(1).expectComplete()
    }

    "see the creation of new documents with non-ASCII id" in new freshDb {
      val changes = db.continuousChanges()
      val result = changes.map(j ⇒ (j \ "id").as[String]).take(3).runFold[List[String]](Nil)(_ :+ _)
      waitEventually(db.insert(JsObject(Nil), "docidé"), db.insert(JsObject(Nil), "docidà"), db.insert(JsObject(Nil), "docidß"))
      waitForResult(result).sorted must be equalTo List("docidß", "docidà", "docidé")
    }

    "allow the specification of a timeout" in new freshDb {
      val result = db.continuousChanges(Map("timeout" → "10")).runWith(Sink.ignore)
      Await.result(result, 500.milliseconds) must be equalTo Done
    }

    "allow the specification of a timeout with explicit erasure of the heartbeat" in new freshDb {
      val result = db.continuousChanges(Map("timeout" → "10", "heartbeat" → "")).runWith(Sink.ignore)
      Await.result(result, 500.milliseconds) must be equalTo Done
    }

    "allow the erasure of the heartbeat without a timeout" in new freshDb {
      def result = db.continuousChanges(Map("heartbeat" → "")).idleTimeout(200.milliseconds).runWith(Sink.ignore)
      Await.result(result, 500.milliseconds) must throwA[TimeoutException]
    }

    "allow the specification of a heartbeat without a timeout" in new freshDb {
      def result = db.continuousChanges(Map("heartbeat" → "30000")).idleTimeout(200.milliseconds).runWith(Sink.ignore)
      Await.result(result, 500.milliseconds) must throwA[TimeoutException]
    }

    "allow the specification of a heartbeat" in new freshDb {
      val result = db.continuousChanges(Map("timeout" → "10", "heartbeat" → "30000")).runWith(Sink.ignore)
      Await.result(result, 500.milliseconds) must throwA[TimeoutException]
    }

    "properly disconnect after a timeout" in new freshDb {
      val changes = db.continuousChanges(Map("timeout" → "100"))
      val result = changes.map(_ \ "id").collect { case JsDefined(JsString(id)) ⇒ id }.runFold[List[String]](Nil)(_ :+ _)
      waitForResult(result).sorted must be equalTo List()
    }

    "see documents operations occurring before the timeout" in new freshDb {
      waitForEnd(db.insert(JsObject(Nil), "docid1"), db.insert(JsObject(Nil), "docid2"))
      val changes = db.continuousChanges(Map("timeout" → "100"))
      val result = changes.map(_ \ "id").collect { case JsDefined(JsString(id)) ⇒ id }.runFold[List[String]](Nil)(_ :+ _)
      waitForResult(result).sorted must be equalTo List("docid1", "docid2")
    }

    "be able to filter changes with a stored filter" in new freshDb {
      val filter = """function(doc, req) { return doc.name == "foo"; }"""
      waitForEnd(db.insert(Json.obj("filters" → Json.obj("f" → filter)), "_design/d"))
      val changes = db.continuousChanges(Map("filter" → "d/f"))
      val result = changes.map(j ⇒ (j \ "id").as[String]).take(2).runFold[List[String]](Nil)(_ :+ _)
      waitEventually(db.bulkDocs(Seq(Json.obj("name" → "foo", "_id" → "docid1"), Json.obj("name" → "bar", "_id" → "docid2"),
                                     Json.obj("name" → "foo", "_id" → "docid3"), Json.obj("name" → "bar", "_id" → "docid4"))))
      waitForResult(result).sorted must be equalTo List("docid1", "docid3")
    }

    "be able to filter changes by document ids" in new freshDb {
      val filter = """function(doc, req) { return doc.name == "foo"; }"""
      val changes = db.continuousChangesByDocIds(List("docid1", "docid4"))
      val result = changes.map(j ⇒ (j \ "id").as[String]).take(2).runFold[List[String]](Nil)(_ :+ _)
      waitEventually(db.bulkDocs(Seq(Json.obj("name" → "foo", "_id" → "docid1"), Json.obj("name" → "bar", "_id" → "docid2"),
                                     Json.obj("name" → "foo", "_id" → "docid3"), Json.obj("name" → "bar", "_id" → "docid4"))))
      waitForResult(result).sorted must be equalTo List("docid1", "docid4")
    }

    "report connection success through the materialized value" in new freshDb {
      val result = db.continuousChanges().toMat(Sink.ignore)(Keep.left).run()
      // In CouchDB 2.x, the HTTP response of the _changes field is sent only after the first change is seen
      db.insert(Json.obj())
      waitForResult(result) must be equalTo Done
    }

    "report a missing database through the materialized value" in new freshDb {
      val newDb = db.couch.db("nonexistent-database")
      val result = newDb.continuousChanges().toMat(Sink.ignore)(Keep.left).run()
      waitForResult(result) must throwA[StatusError]("404 .*: not_found")
    }

    "report a connection error through the materialized value" in new freshDb {
      val newDb = new Couch("localhost", 5985).db("not-running-anyway")
      val result = newDb.continuousChanges().toMat(Sink.ignore)(Keep.left).run()
      waitForResult(result) must throwA[RuntimeException]
    }

    "fail properly if the database is absent" in new freshDb {
      val newDb = db.couch.db("nonexistent-database")
      val result = newDb.continuousChanges().runFold[List[JsObject]](Nil)(_ :+ _)
      waitForResult(result) must throwA[StatusError]("404 .*: not_found")
    }

    "fail properly if the HTTP server is not running" in {
      val newDb = new Couch("localhost", 5985).db("not-running-anyway")
      val result = newDb.continuousChanges().runFold[List[JsObject]](Nil)(_ :+ _)
      waitForResult(result) must throwA[Exception]
    }

    "fail properly if the HTTPS server is not running" in {
      val newDb = new Couch("localhost", 5985, secure = true).db("not-running-anyway")
      val result = newDb.continuousChanges().runFold[List[JsObject]](Nil)(_ :+ _)
      waitForResult(result) must throwA[Exception]
    }

    "terminate properly if the database is deleted during the request" in new freshDb {
      // CouchDB 2.0.0-RC4 sends garbage on the connection if the database is deleted during the request.
      // See issue COUCHDB-3132.
      pendingIfNotCouchDB1("garbage will be sent on the connection")
      val result = db.continuousChanges().runFold[List[JsObject]](Nil)(_ :+ _)
      waitForResult(db.insert(JsObject(Nil), "docid1"))
      waitForResult(db.insert(JsObject(Nil), "docid2"))
      waitForResult(db.insert(JsObject(Nil), "docid3"))
      waitForResult(db.delete())
      val changes = waitForResult(result)
      changes must haveSize(4)
      (changes.last \ "last_seq").as[Long] must beEqualTo(3)
    }

  }

}
