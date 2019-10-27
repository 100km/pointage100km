package replicate.messaging

import akka.http.scaladsl.model.Uri
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import replicate.messaging.Message.{Category, Severity}
import replicate.utils.Glyphs

case class Message(category: Category, severity: Severity.Severity, title: String, body: String, url: Option[Uri] = None, icon: Option[String] = None) {

  override lazy val toString = s"[$titleWithSeverity] $body${url.fold("")(l => s" ($l)")}"

  lazy val titleWithSeverity: String = if (severity >= Severity.Warning) s"$severity: $title" else title

  def severityIcon: Option[String] = Message.severityIcons(severity)

  def severityOrMessageIcon: Option[String] = severityIcon orElse icon
}

object Message {

  private[this] var categories: Map[String, Category] = Map()

  sealed trait Category {
    override val toString = {
      val name = getClass.getSimpleName.dropRight(1)
      // Transform CamelCase into snake_case
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
  case object TextMessage extends Category

  val allCategories: Seq[Category] = categories.values.toSeq

  object Severity extends Enumeration {
    type Severity = Value
    val Debug, Verbose, Info, Warning, Error, Critical = Value
  }

  private[this] val severities: Map[String, Severity.Value] = Severity.values.map(severity => severity.toString.toLowerCase -> severity).toMap

  private val severityIcons: Map[Severity.Value, Option[String]] = Map(
    Severity.Debug -> None,
    Severity.Verbose -> None,
    Severity.Info -> None,
    Severity.Warning -> Some(Glyphs.warningSign),
    Severity.Error -> Some(Glyphs.bomb),
    Severity.Critical -> Some(Glyphs.collisionSymbol))

  implicit val messageReads: Reads[Message] = (
    (JsPath \ "category").read[String].map(categories) and
    (JsPath \ "severity").read[String].map(severities) and
    (JsPath \ "title").read[String] and
    (JsPath \ "body").read[String] and
    (JsPath \ "url").readNullable[String].map(_.map(Uri.apply)) and
    (JsPath \ "icon").readNullable[String])(Message.apply _)

  implicit val categoryWrites: Writes[Category] = Writes { category => JsString(category.toString) }
  implicit val severityWrites: Writes[Severity.Severity] = Writes { severity => JsString(severity.toString.toLowerCase) }
  implicit val uriWrites: Writes[Uri] = Writes { uri => JsString(uri.toString) }
  implicit val messageWrites: Writes[Message] = (
    (JsPath \ "category").write[Category] and
    (JsPath \ "severity").write[Severity.Severity] and
    (JsPath \ "title").write[String] and
    (JsPath \ "body").write[String] and
    (JsPath \ "url").writeNullable[Uri] and
    (JsPath \ "icon").writeNullable[String])(unlift(Message.unapply))
}
