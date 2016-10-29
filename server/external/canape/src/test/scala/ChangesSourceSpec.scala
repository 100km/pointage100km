import akka.Done
import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{OverflowStrategy, ThrottleMode}
import com.typesafe.config.ConfigFactory
import net.rfc1149.canape.Couch.StatusError
import net.rfc1149.canape.{ChangesSource, Couch, Database}
import net.rfc1149.canape.Database.FromStart
import org.specs2.mock._
import play.api.libs.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class ChangesSourceSpec extends WithDbSpecification("db") with Mockito {

  def addDone[T, M](source: Source[T, M]): Source[T, Future[Done]] =
    source.mapMaterializedValue(_ ⇒ FastFuture.successful(Done))

  "db.changesSource()" should {

    "respect the since parameter" in new freshDb {
      waitForResult(db.insert(JsObject(Nil), "docid0"))
      val changes: Source[JsObject, Future[Done]] = db.changesSource(sinceSeq = FromStart)
      val result = changes.map(j ⇒ (j \ "id").as[String]).take(4).runFold[List[String]](Nil)(_ :+ _)
      waitEventually(db.insert(JsObject(Nil), "docid1"), db.insert(JsObject(Nil), "docid2"), db.insert(JsObject(Nil), "docid3"))
      waitForResult(result).sorted must be equalTo List("docid0", "docid1", "docid2", "docid3")
    }

    "signal the connection without the initial since parameter" in new freshDb {
      waitForEnd(db.insert(JsObject(Nil), "docid0"))
      val changes: Source[JsObject, Future[Done]] = db.changesSource()
      val (done, result) = changes.map(j ⇒ (j \ "id").as[String]).take(3).toMat(Sink.fold[List[String], String](Nil)(_ :+ _))(Keep.both).run()

      // CouchDB 2.0.0-RC4 does not send an acknowledgement until a change is potentially available (it may be blocked
      // by a filter). So we cannot wait for connection, we have to use a sleep to ensure that the changes feed is connected.
      // See issue COUCHDB-3133.
      // waitForResult(done)
      try Thread.sleep(1000) catch { case _: InterruptedException ⇒ }

      waitEventually(db.insert(JsObject(Nil), "docid1"), db.insert(JsObject(Nil), "docid2"), db.insert(JsObject(Nil), "docid3"))
      waitForResult(result).sorted must be equalTo List("docid1", "docid2", "docid3")
    }

    "reconnect in case of a timeout" in new freshDb {
      waitForResult(db.insert(JsObject(Nil), "docid0"))
      val changes: Source[JsObject, Future[Done]] = db.changesSource(sinceSeq = FromStart, params = Map("timeout" → "1"))
      val (done, result) = changes.map(j ⇒ (j \ "id").as[String]).take(4).toMat(Sink.fold[List[String], String](Nil)(_ :+ _))(Keep.both).run()
      waitForResult(done)
      waitEventually(db.insert(JsObject(Nil), "docid1"), db.insert(JsObject(Nil), "docid2"), db.insert(JsObject(Nil), "docid3"))
      waitForResult(result).sorted must be equalTo List("docid0", "docid1", "docid2", "docid3")
    }

    "terminate on error if the database is deleted" in new freshDb {
      pendingIfNotCouchDB1("garbage will be sent on the connection")
      waitForResult(db.insert(JsObject(Nil), "docid0"))
      val changes: Source[JsObject, Future[Done]] = db.changesSource()
      val result = changes.runWith(Sink.ignore)
      waitForResult(db.insert(JsObject(Nil), "docid1"))
      waitForResult(db.insert(JsObject(Nil), "docid2"))
      waitForResult(db.insert(JsObject(Nil), "docid3"))
      waitForResult(db.delete())
      waitForResult(result) must throwA[StatusError]("404 no_db_file: not_found")
    }

    "return the existing documents before the error if the database is deleted" in new freshDb {
      // CouchDB 2.0.0-RC4 sends garbage on the connection if the database is deleted during the request.
      // See issue COUCHDB-3132.
      pendingIfNotCouchDB1("garbage will be sent on the connection")
      waitForResult(db.insert(JsObject(Nil), "docid0"))
      val changes: Source[JsObject, Future[Done]] = db.changesSource(sinceSeq = FromStart).recoverWithRetries(-1, { case _ ⇒ Source.empty })
      val result = changes.map(j ⇒ (j \ "id").as[String]).runFold[List[String]](Nil)(_ :+ _)
      waitForResult(db.insert(JsObject(Nil), "docid1"))
      waitForResult(db.insert(JsObject(Nil), "docid2"))
      waitForResult(db.insert(JsObject(Nil), "docid3"))
      waitForResult(db.delete())
      waitForResult(result).sorted must be equalTo List("docid0", "docid1", "docid2", "docid3")
    }

    "see the creation of new documents as soon as they are created" in new freshDb {
      val changes: Source[JsObject, Future[Done]] = db.changesSource(sinceSeq = FromStart)
      val downstream = changes.map(j ⇒ (j \ "id").as[String]).take(3).runWith(TestSink.probe)
      waitEventually(db.insert(JsObject(Nil), "docid1"))
      downstream.requestNext("docid1")
      waitEventually(db.insert(JsObject(Nil), "docid2"))
      downstream.requestNext("docid2")
      waitEventually(db.insert(JsObject(Nil), "docid3"))
      downstream.requestNext("docid3")
      downstream.request(1).expectComplete()
    }

    "reconnect after an error" in new freshDb {

      val config = ConfigFactory.parseString("changes-source.reconnection-delay=50ms")
      val mockedCouch: Couch = mock[Couch].canapeConfig returns config
      val mockedDb = mock[Database]
      val sourceWithError = Source(List(
        Source.repeat(Json.obj("seq" → 42, "id" → "someid")).take(100),
        Source.failed(new RuntimeException())
      )).flatMapConcat(identity)
      mockedDb.continuousChanges(org.mockito.Matchers.anyObject(), org.mockito.Matchers.anyObject()) returns
        addDone(sourceWithError)
      mockedDb.couch returns mockedCouch

      val changes: Source[JsObject, ActorRef] = Source.actorPublisher(Props(new ChangesSource(mockedDb, sinceSeq = FromStart)))
      val result = changes.map(j ⇒ (j \ "id").as[String]).take(950).runFold(0) { case (n, _) ⇒ n + 1 }
      waitForResult(result) must be equalTo 950
      there was atLeast(10)(mockedDb).continuousChanges(org.mockito.Matchers.anyObject(), org.mockito.Matchers.anyObject())
    }

    "see everything up-to the error" in new freshDb {

      val config = ConfigFactory.parseString("changes-source.reconnection-delay=50ms")
      val mockedCouch: Couch = mock[Couch].canapeConfig returns config
      val mockedDb = mock[Database]
      val sourceWithError = Source(List(
        Source(1 to 10).map(n ⇒ Json.obj("seq" → JsNumber(30 + n))),
        Source.failed(new RuntimeException())
      )).flatMapConcat(identity)
      mockedDb.continuousChanges(org.mockito.Matchers.anyObject(), org.mockito.Matchers.anyObject()) returns
        addDone(sourceWithError) thenReturns
        addDone(Source(1 to 5).map(n ⇒ Json.obj("seq" → JsNumber(n))))
      mockedDb.couch returns mockedCouch

      val changes: Source[JsObject, ActorRef] = Source.actorPublisher(Props(new ChangesSource(mockedDb, sinceSeq = FromStart)))
      val result = changes.map(j ⇒ (j \ "seq").as[Long]).take(15).runFold(0L) { case (n, e) ⇒ n.max(e) }
      waitForResult(result) must be equalTo 40
      there was atLeast(2)(mockedDb).continuousChanges(org.mockito.Matchers.anyObject(), org.mockito.Matchers.anyObject())
    }

    "handle errors due to backpressure" in new freshDb {

      val config = ConfigFactory.parseString("changes-source.reconnection-delay=200ms")
      val mockedCouch: Couch = mock[Couch].canapeConfig returns config
      val mockedDb = mock[Database]
      mockedDb.continuousChanges(org.mockito.Matchers.anyObject(), org.mockito.Matchers.anyObject()) returns
        addDone(Source.repeat(Json.obj("seq" → 42, "id" → "someid")).buffer(10, OverflowStrategy.fail))
      mockedDb.couch returns mockedCouch

      val changes: Source[JsObject, ActorRef] = Source.actorPublisher(Props(new ChangesSource(mockedDb, sinceSeq = FromStart)))
      val result = changes.throttle(100, 1.second, 100, ThrottleMode.Shaping).map(j ⇒ (j \ "id").as[String]).take(120).runFold(0) { case (n, _) ⇒ n + 1 }
      Await.result(result, 15.seconds) must be equalTo 120
      there was atLeast(2)(mockedDb).continuousChanges(org.mockito.Matchers.anyObject(), org.mockito.Matchers.anyObject())
    }

    "see the creation of new documents with non-ASCII id" in new freshDb {
      val changes: Source[JsObject, Future[Done]] = db.changesSource(sinceSeq = FromStart)
      val result = changes.map(j ⇒ (j \ "id").as[String]).take(3).runFold[List[String]](Nil)(_ :+ _)
      waitEventually(db.insert(JsObject(Nil), "docidé"), db.insert(JsObject(Nil), "docidà"), db.insert(JsObject(Nil), "docidß"))
      waitForResult(result).sorted must be equalTo List("docidß", "docidà", "docidé")
    }

    "be able to filter changes with a stored filter" in new freshDb {
      val filter = """function(doc, req) { return doc.name == "foo"; }"""
      waitForEnd(db.insert(Json.obj("filters" → Json.obj("namedfoo" → filter)), "_design/common"))
      val changes: Source[JsObject, Future[Done]] = db.changesSource(sinceSeq = FromStart, params = Map("filter" → "common/namedfoo"))
      val result = changes.map(j ⇒ (j \ "id").as[String]).take(2).runFold[List[String]](Nil)(_ :+ _)
      waitEventually(db.bulkDocs(Seq(Json.obj("name" → "foo", "_id" → "docid1"), Json.obj("name" → "bar", "_id" → "docid2"),
        Json.obj("name" → "foo", "_id" → "docid3"), Json.obj("name" → "bar", "_id" → "docid4"))))
      waitForResult(result).sorted must be equalTo List("docid1", "docid3")
    }

    "be able to filter changes by document ids" in new freshDb {
      val filter = """function(doc, req) { return doc.name == "foo"; }"""
      val changes: Source[JsObject, Future[Done]] = db.changesSourceByDocIds(List("docid1", "docid4"), sinceSeq = FromStart)
      val result = changes.map(j ⇒ (j \ "id").as[String]).take(2).runFold[List[String]](Nil)(_ :+ _)
      waitEventually(db.bulkDocs(Seq(Json.obj("name" → "foo", "_id" → "docid1"), Json.obj("name" → "bar", "_id" → "docid2"),
        Json.obj("name" → "foo", "_id" → "docid3"), Json.obj("name" → "bar", "_id" → "docid4"))))
      waitForResult(result).sorted must be equalTo List("docid1", "docid4")
    }

    "fail properly if the database is absent" in new freshDb {
      val newDb = db.couch.db("nonexistent-database")
      val result = newDb.changesSource().runFold[List[JsObject]](Nil)(_ :+ _)
      waitForResult(result) must throwA[StatusError]("404 .*: not_found")
    }

  }

}
