package replicate.messaging

import akka.http.scaladsl.model.Uri
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import replicate.messaging.Message._

case class Message(category: Category, severity: Severity.Severity, title: String, body: String, url: Option[Uri]) {

  override lazy val toString = s"[$titleWithSeverity] $body${url.fold("")(l => s" ($l)")}"

  lazy val titleWithSeverity: String = if (severity >= Severity.Warning) s"$severity: $title" else title
}

object Message {

  private[this] var categories: Map[String, Category] = Map()

  sealed trait Category {
    override val toString = {
      val name = getClass.getSimpleName.dropRight(1)
      (name.head +: "[A-Z]".r.replaceAllIn(name.tail, m => s"_${m.group(0)}")).mkString.toLowerCase
    }

    categories += toString -> this
  }

  // Categories and severities must be kept synchronous with the admin/officers view

  case object Administrativia extends Category
  case object Broadcast extends Category
  case object Checkpoint extends Category
  case object Connectivity extends Category
  case object RaceInfo extends Category

  val allCategories: Seq[Category] = categories.values.toSeq

  object Severity extends Enumeration {
    type Severity = Value
    val Debug, Verbose, Info, Warning, Error, Critical = Value
  }

  private[this] val severities: Map[String, Severity.Value] = Severity.values.map(severity => severity.toString -> severity).toMap

  implicit val messageReads: Reads[Message] = (
    (JsPath \ "category").read[String].map(categories) and
      (JsPath \ "severity").read[String].map(severities) and
      (JsPath \ "title").read[String] and
      (JsPath \ "body").read[String] and
      (JsPath \ "url").readNullable[String].map(_.map(Uri.apply))
    )(Message.apply _)

  implicit val categoryWrites: Writes[Category] = Writes { category => JsString(category.toString) }
  implicit val severityWrites: Writes[Severity.Severity] = Writes { severity => JsString(severity.toString) }
  implicit val uriWrites: Writes[Uri] = Writes { uri => JsString(uri.toString) }
  implicit val messageWrites: Writes[Message] = (
    (JsPath \ "category").write[Category] and
      (JsPath \ "severity").write[Severity.Severity] and
      (JsPath \ "title").write[String] and
      (JsPath \ "body").write[String] and
      (JsPath \ "url").writeNullable[Uri]
  )(unlift(Message.unapply))
}
