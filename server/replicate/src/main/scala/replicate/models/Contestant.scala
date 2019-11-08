package replicate.models

import play.api.libs.json._
import play.api.libs.functional.syntax._
import replicate.utils.Types._

import scalaz.@@

case class Contestant(first_name: String, name: String, bib: Int @@ ContestantId, race: Int @@ RaceId, stalkers: List[String @@ PhoneNumber]) {
  def full_name = s"$first_name $name"
  def full_name_and_bib = s"$full_name ($bib)"
  def contestantId = bib
  def raceId = race
  override def toString = full_name_and_bib
}
object Contestant {
  // XXXXX Why doesn't this work? It used to before Scala 2.13
  // implicit val contestantReads: Reads[Contestant] = Json.reads[Contestant]

  implicit val contestantReads: Reads[Contestant] = Reads { js =>
    try {
      val first_name = (js \ "first_name").as[String]
      val name = (js \ "name").as[String]
      val bib = ContestantId((js \ "bib").as[Int])
      val race = RaceId((js \ "race").as[Int])
      val stalkers = (js \ "stalkers").as[List[String]].map(PhoneNumber.apply)
      JsSuccess(Contestant(first_name, name, bib, race, stalkers))
    } catch {
      case t: Throwable => JsError(t.getMessage)
    }
  }
}
