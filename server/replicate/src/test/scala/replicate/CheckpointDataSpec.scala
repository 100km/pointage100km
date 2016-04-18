package replicate

import org.specs2.mutable._
import play.api.libs.json.Json
import replicate.models.CheckpointData

class CheckpointDataSpec extends Specification {

  private val js = Json.parse(
    """
      | {
      |   "_id": "checkpoints-3-42",
      |   "type": "checkpoint",
      |   "race_id": 2,
      |   "bib": 42,
      |   "site_id": 3,
      |   "times": [1000, 1001],
      |   "deleted_times": [1002],
      |   "artificial_times": [1001]
      | }
    """.stripMargin
  )

  private val partialJs = Json.parse(
    """
      | {
      |   "_id": "checkpoints-3-42",
      |   "type": "checkpoint",
      |   "race_id": 2,
      |   "bib": 42,
      |   "site_id": 3,
      |   "times": [1000, 1001]
      | }
    """.stripMargin
  )

  "CheckpointData" should {

    "be readable from a Json document" in {
      val cpd = js.as[CheckpointData]
      cpd.raceId must be equalTo 2
      cpd.contestantId must be equalTo 42
      cpd.siteId must be equalTo 3
      cpd.timestamps must be equalTo List(1000, 1001)
      cpd.deletedTimestamps must be equalTo List(1002)
      cpd.insertedTimestamps must be equalTo List(1001)
    }

    "be readable from an incomplete Json document" in {
      val cpd = partialJs.as[CheckpointData]
      cpd.raceId must be equalTo 2
      cpd.contestantId must be equalTo 42
      cpd.siteId must be equalTo 3
      cpd.timestamps must be equalTo List(1000, 1001)
      cpd.deletedTimestamps must be equalTo Nil
      cpd.insertedTimestamps must be equalTo Nil
    }

    "be readable from its own Json dump" in {
      val cpd = js.as[CheckpointData]
      val cpd2 = Json.toJson(cpd).as[CheckpointData]
      cpd must be equalTo cpd2
    }

    "be readable from its own Json dump when starting from an incomplete document" in {
      val cpd = partialJs.as[CheckpointData]
      val cpd2 = Json.toJson(cpd).as[CheckpointData]
      cpd must be equalTo cpd2
    }

    "be transformable into a pristine object" in {
      val cpd = js.as[CheckpointData].pristine
      cpd.raceId must be equalTo 2
      cpd.contestantId must be equalTo 42
      cpd.siteId must be equalTo 3
      cpd.timestamps must be equalTo List(1000, 1002)
      cpd.deletedTimestamps must be equalTo Nil
      cpd.insertedTimestamps must be equalTo Nil
    }

    "be mergeable with another checkpoint" in {
      val cpd = js.as[CheckpointData]
      val cpd2 = CheckpointData(2, 42, 3, List(1002, 1003, 1004), List(1000), List(1004))
      cpd.merge(cpd2) must be equalTo CheckpointData(2, 42, 3, List(1001, 1003, 1004), List(1000, 1002), List(1001, 1004))
    }

    "be idempotent while merging itself" in {
      val cpd = js.as[CheckpointData]
      cpd.merge(cpd) must be equalTo cpd
    }

  }
}
