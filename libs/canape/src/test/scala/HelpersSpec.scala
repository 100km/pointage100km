import dispatch._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import org.specs2.mutable._
import org.specs2.specification._

class HelpersSpec extends DbSpecification {

  implicit val formats = DefaultFormats

  import helpers._

  val dbSuffix = "helperstest"

  def makeConflicts() =
    http(db.bulkDocs(Seq(("_id" -> "docid") ~ ("extra" -> List("one")),
			 ("_id" -> "docid") ~ ("extra" -> List("other")),
			 ("_id" -> "docid") ~ ("extra" -> List("yet-another"))),
		     true))

  "getRevs()" should {

    "return all the revisions" in {
      makeConflicts()
      http(getRevs(db, "docid")) must have size(3)
    }

    "return the selected revisions" in {
      makeConflicts()
      val revs = (http(db("docid", Map("conflicts" -> "true"))) \ "_conflicts").extract[List[String]]
      http(getRevs(db, "docid", revs)) must have size(2)
    }

  }

  "getConflicting()" should {

    "return the list of conflicting documents by id" in {
      makeConflicts()
      http(getConflictingRevs(db, "docid")) must have size(3)
    }

  }

  "solve()" should {

    "be able to solve a conflict" in {
      makeConflicts()
      val revs = http(getConflictingRevs(db, "docid"))
      val docs = http(getRevs(db, "docid", revs))
      http(solve(db, docs) { docs => docs.head })
      http(getConflictingRevs(db, "docid")) must have size(1)
    }

    "be able to merge documents" in {
      makeConflicts()
      val revs = http(getConflictingRevs(db, "docid"))
      val docs = http(getRevs(db, "docid", revs))
      http(solve(db, docs) { docs =>
	val extra = docs.map{ js: JObject => (js \ "extra").extract[List[String]] }.flatten
	docs.head.replace("extra" :: Nil, extra.sorted).asInstanceOf[JObject]
      })
      (http(db("docid")) \ "extra").extract[List[String]] must be equalTo(List("one", "other", "yet-another"))
    }

  }

}
