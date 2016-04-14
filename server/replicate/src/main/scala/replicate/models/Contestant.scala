package replicate.models

import play.api.libs.json.{Json, Reads}

case class Contestant(first_name: String, name: String, bib: Int, race: Int, stalkers: List[String]) {
  def full_name = s"$first_name $name"
  def full_name_and_bib = s"$full_name (dossard $bib)"
  def contestantId = bib
  def raceId = race
}

object Contestant {
  implicit val contestantReads: Reads[Contestant] = Json.reads[Contestant]
}
