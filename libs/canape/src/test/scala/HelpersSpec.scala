import net.liftweb.json._
import net.rfc1149.canape._

class HelpersSpec extends DbSpecification {

  import helpers._
  import implicits._

  val dbSuffix = "helperstest"

  def makeConflicts() =
    db.bulkDocs(Seq(Map("_id" -> "docid", "extra" -> List("one")),
      Map("_id" -> "docid", "extra" -> List("other")),
      Map("_id" -> "docid", "extra" -> List("yet-another"))),
      true).execute()

  "getRevs()" should {

    "return all the revisions" in {
      makeConflicts()
      getRevs(db, "docid").execute must have size(3)
    }

    "return the selected revisions" in {
      makeConflicts()
      val revs = db("docid", Map("conflicts" -> "true")).execute().subSeq[String]("_conflicts")
      getRevs(db, "docid", revs).execute must have size(2)
    }

  }

  "getConflicting()" should {

    "return the list of conflicting documents by id" in {
      makeConflicts()
      val doc = db("docid", Map("conflicts" -> "true")).execute().asInstanceOf[JObject]
      getConflicting(db, doc).execute must have size(3)
    }

  }

  "solve()" should {

    "be able to solve a conflict" in {
      makeConflicts()
      val revs = getConflictingRevs(db, "docid").execute()
      val docs = getRevs(db, "docid", revs).execute()
      (solve(db, docs) {
        docs => docs.head
      }).execute()
      getConflictingRevs(db, "docid").execute must have size(1)
    }

    "be able to merge documents" in {
      makeConflicts()
      val revs = getConflictingRevs(db, "docid").execute()
      val docs = getRevs(db, "docid", revs).execute()
      (solve(db, docs) {
        docs =>
          val extra = docs.map {
            _("extra").children.map(_.extract[String])
          }.flatten.sorted
          docs.head + ("extra" -> JArray(extra.map(JString(_)).toList))
      }).execute()
      db("docid").execute()("extra") must be equalTo(parse("""["one", "other", "yet-another"]"""))
    }

  }

}
