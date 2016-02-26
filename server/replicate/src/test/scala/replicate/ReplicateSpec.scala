package replicate

import java.util.UUID

import net.rfc1149.canape.{Database, helpers}
import org.specs2.mutable._
import play.api.libs.json.{JsObject, JsValue, Json}
import replicate.utils.Options

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class ReplicateSpec extends Specification with After {

  val options = Options.Config(compactLocal = false, dryRun = false, _fixConflicts = true, _fixIncomplete = true,
    _obsolete = false, replicate = true, siteId = 1000, _ping = false)

  val replicate: Replicate = new Replicate(options)

  val db: Database = replicate.localDatabase

  override def after = {
    // Global.system.terminate
  }

  trait WithCleanup extends BeforeAfter {

    var idsToCleanup: Seq[String] = Seq()

    def get(doc: JsObject): Future[JsObject] = db((doc \ "_id").as[String])

    def delete(id: String): Future[Seq[JsValue]] = {
      helpers.getRevs(db, id).map(_.filterNot(_.keys.contains("_deleted"))).flatMap(ds => Future.sequence(ds.map(db.delete)))
    }

    def delete(doc: JsObject): Future[Seq[JsValue]] = delete((doc \ "_id").as[String])

    def bulkDelete(ids: Seq[String]): Future[Seq[JsValue]] = Future.sequence(ids.map(delete)).map(_.flatten)

    def delInsert(doc: JsObject): Future[JsValue] = {
      val id = (doc \ "_id").as[String]
      idsToCleanup :+= id
      delete(id).flatMap(_ => db.insert(doc))
    }

    def bulkDelInsert(docs: JsObject*): Future[Seq[JsObject]] = {
      val ids = docs.map(d => (d \ "_id").as[String]).distinct
      idsToCleanup ++= ids
      bulkDelete(ids).flatMap { _ => db.bulkDocs(docs, allOrNothing = true) }
    }

    def waitForResult[A](a: Future[A]): A = Await.result(a, (5, SECONDS))

    override def before = {
      if (!waitForResult(steenwerck.testsAllowed(db))) {
        skipped("Skipping: tests are not allowed in this database (configuration/tests_allowed must be true)")
      }
    }

    override def after = waitForResult(bulkDelete(idsToCleanup))

  }

  "Replicate" should {

    "complete missing checkpoints" in new WithCleanup {
      val contestant = Json.obj("first_name" -> "John", "name" -> "Doe", "bib" -> 10000,
        "race" -> 17, "_id" -> "contestant-10000", "type" -> "contestant")
      waitForResult(delete(contestant))
      val checkpoint = Json.obj("_id" -> "checkpoint-99-10000", "bib" -> 10000, "race_id" -> 0, "site_id" -> 99,
        "times" -> Array(12345), "bib" -> 10000)
      waitForResult(delInsert(checkpoint))
      waitForResult(delInsert(contestant))
      Thread.sleep(6000)
      val c = waitForResult(get(checkpoint))
      (c \ "race_id").as[Int] must be equalTo 17
    }

    "fix conflicting checkpoints" in new WithCleanup {
      val base = Json.obj("_id" -> "checkpoint-98-11000", "bib" -> 11000, "race_id" -> 19, "site_id" -> 98,
        "uuid" -> UUID.randomUUID().toString)
      val cp1 = base ++ Json.obj("times" -> Array(1, 2, 8), "deleted_times" -> Array(4))
      val cp2 = base ++ Json.obj("times" -> Array(4, 5, 7), "artificial_times" -> Array(5))
      val cp3 = base ++ Json.obj("times" -> Array(3, 6, 9))
      waitForResult(bulkDelInsert(cp1, cp2, cp3))
      Thread.sleep(6000)
      val c = waitForResult(get(cp1))
      (c \ "times").as[List[Int]] must be equalTo List(1, 2, 3, 5, 6, 7, 8, 9)
      (c \ "deleted_times").as[List[Int]] must be equalTo List(4)
      (c \ "artificial_times").as[List[Int]] must be equalTo List(5)
    }

  }

}
