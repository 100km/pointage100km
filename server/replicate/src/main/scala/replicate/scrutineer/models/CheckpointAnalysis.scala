package replicate.scrutineer.models

import play.api.libs.json.{JsObject, Json}

case class CheckpointAnalysis(siteId: Int, lap: Int, distance: Double, timestamp: Long, status: CheckpointStatus) {
  def toJson: JsObject =
    Json.obj("site_id" → siteId, "timestamp" → timestamp, "lap" → lap, "distance" → distance) ++ status.toJson
}
