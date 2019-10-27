package replicate.utils

import play.api.libs.json.{Reads, Writes}

import scalaz.{@@, Tag}

object Types {

  sealed trait ContestantId
  val ContestantId = Tag.of[ContestantId]

  sealed trait Lap
  val Lap = Tag.of[Lap]

  sealed trait RaceId
  val RaceId = Tag.of[RaceId]

  sealed trait SiteId
  val SiteId = Tag.of[SiteId]

  sealed trait PhoneNumber
  val PhoneNumber = Tag.of[PhoneNumber]

  // Tagged instances are read and written without their tag
  implicit def taggedReads[A: Reads, T]: Reads[A @@ T] = Reads { js => js.validate[A].map(Tag.of[T](_)) }
  implicit def taggedWrites[A: Writes, T]: Writes[A @@ T] = Writes { a => implicitly[Writes[A]].writes(Tag.of[T].unwrap(a)) }

}
