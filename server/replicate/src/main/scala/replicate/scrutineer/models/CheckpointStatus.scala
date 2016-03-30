package replicate.scrutineer.models

import play.api.libs.json.{JsObject, Json}

sealed abstract class CheckpointStatus {
  def toJson: JsObject
  val isOk: Boolean = false
}

object CheckpointStatus {

  case class Ok(speed: Double) extends CheckpointStatus {
    def toJson = Json.obj("type" → "ok", "speed" → speed)
    override val isOk: Boolean = true
  }

  case class Suspicious(speed: Double) extends CheckpointStatus {
    def toJson = Json.obj("type" → "suspicious", "speed" → speed)
  }

  case object Missing extends CheckpointStatus {
    def toJson = Json.obj("type" → "missing")
  }

  case object Down extends CheckpointStatus {
    def toJson = Json.obj("type" → "down")
  }

  case object TooEarly extends CheckpointStatus {
    def toJson = Json.obj("type" → "early")
  }

  case object TooLate extends CheckpointStatus {
    def toJson = Json.obj("type" → "late")
  }

  case object TooLong extends CheckpointStatus {
    def toJson = Json.obj("type" → "long")
  }

}
