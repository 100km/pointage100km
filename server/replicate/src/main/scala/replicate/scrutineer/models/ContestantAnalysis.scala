package replicate.scrutineer.models

import play.api.libs.json.Json

case class ContestantAnalysis(contestantId: Int, raceId: Int, checkpoints: Seq[CheckpointAnalysis]) {
  def toJson = Json.obj("type" → "problem", "bib" → contestantId, "race_id" → raceId,
    "checkpoints" → Json.arr(checkpoints.map(_.toJson)))

  def isOk = checkpoints.forall(_.status.isOk)

  def id = s"problem-$contestantId"
}
