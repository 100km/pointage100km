package replicate.stalking

import java.util.{Calendar, TimeZone}

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, ThrottleMode}
import net.ceedubs.ficus.Ficus._
import net.rfc1149.canape.{Couch, Database}
import play.api.libs.json.{JsObject, JsValue}
import replicate.alerts.RankingAlert
import replicate.messaging.PushbulletSMS
import replicate.utils.Global

import scala.concurrent.Future
import scala.concurrent.duration._

class Stalker(database: Database) extends Actor with ActorLogging {

  import Global.dispatcher

  private[this] implicit val fm = ActorMaterializer()

  private[this] var smsActorRef: ActorRef = _

  /**
   * Stalker phone numbers by bib.
   */
  private[this] var stalkers: Map[Long, Seq[String]] = Map()

  /**
   * Stalkee name.
   */
  private[this] var name: Map[Long, String] = Map()

  /**
   * Stalkee race.
   */
  private[this] var race: Map[Long, Int] = Map()

  /**
   * Stalkee position, as site id, time, lap, and rank.
   */
  private[this] var position: Map[Long, (Int, Long, Int, Int)] = Map()

  private[this] var stalkStage: Long = 0

  private[this] def startPushbulletSMS(): Option[ActorRef] = {
    val config = Global.replicateConfig
    for (bearerToken <- config.as[Option[String]]("sms.bearer-token");
         userIden <- config.as[Option[String]]("sms.user-iden");
         deviceIden <- config.as[Option[String]]("sms.device-iden"))
      yield context.actorOf(Props(new PushbulletSMS(bearerToken, userIden, deviceIden)))
  }

  override def preStart(): Unit = {
    startPushbulletSMS() match {
      case Some(actorRef: ActorRef) =>
        log.debug("SMS service started")
        smsActorRef = actorRef
        launchInitialStalkersChanges()
      case None =>
        log.warning("no SMS service")
        context.stop(self)
    }
  }

  private[this] def updateStalkees(doc: JsObject): Boolean = {
    val bib = (doc \ "bib").as[Long]
    val newStalkers = (doc \ "stalkers").as[Seq[String]]
    if (newStalkers != stalkers.getOrElse(bib, Seq())) {
      if (newStalkers.nonEmpty) {
        stalkers += bib -> newStalkers
        name += bib -> s"${(doc \ "first_name").as[String]} ${(doc \ "name").as[String]} (dossard $bib)"
        race += bib -> (doc \ "race").as[Int]
      } else {
        stalkers -= bib
        name -= bib
        position -= bib
        race -= bib
      }
      true
    } else
      false
  }

  private[this] def contestantInfo(bib: Long): Future[(Int, Long, Int, Int)] = {
    val raceInfo = Global.infos.get.races(race(bib))
    for (ranking <- RankingAlert.raceRanking(raceInfo, database).flatMap(Couch.checkResponse[JsObject])
      .map(json => json \\ "value"))
      yield {
        val index = ranking.indexWhere(json => (json \ "bib").as[Long] == bib)
        val doc = ranking(index)
        val siteId = (doc \ "_id").as[String].split("-")(1).toInt
        val times = (doc \ "times").as[Array[Long]]
        val date = times.last
        val lap = times.length
        val rank = index + 1
        (siteId, date, lap, rank)
      }
  }

  private[this] def sendInfo(bib: Long, pos: (Int, Long, Int, Int)): Unit = {
    val recipients = stalkers.getOrElse(bib, Seq())
    if (recipients.nonEmpty) {
      pos match {
        case (siteId, timestamp, lap, rank) =>
          val infos = Global.infos.get
          val raceInfo = infos.races(race(bib))
          if (lap <= raceInfo.laps) {
            val date = Calendar.getInstance()
            date.setTimeInMillis(timestamp)
            date.setTimeZone(TimeZone.getTimeZone(infos.timezone))
            val time = "%d:%02d".format(date.get(Calendar.HOUR_OF_DAY), date.get(Calendar.MINUTE))
            val message = s"""${name(bib)} : dernier pointage au site "${infos.checkpoints(siteId).name}" à $time """ +
              s"(${raceInfo.name}, tour $lap, ${"%.2f".format(infos.distances(siteId, lap))} kms, position $rank)"
            for (recipient <- recipients)
              PushbulletSMS.sendSMS(smsActorRef, recipient, message)
          } else
            log.warning(s"Bib $bib pointed at lap $lap while race ${raceInfo.name} only has ${raceInfo.laps}")
      }
    }
  }

  private[this] def launchInitialStalkersChanges(): Unit =
    pipe(database.status().map(json => (json \ "update_seq").as[Long]).flatMap(lastSeq =>
      database.view[JsValue, JsObject]("admin", "stalked").map(('initial, lastSeq, _)))) to self

  private[this] def launchStalkersChanges(fromSeq: Long): NotUsed = {
    database.changesSource(Map("filter" -> "admin/stalked", "include_docs" -> "true"), sinceSeq = 0)
      .throttle(50, 1.second, 50, ThrottleMode.Shaping)
      .runWith(Sink.actorRef(self, 'ignored))
  }

  private[this] def launchCheckpointChanges(fromSeq: Long): Unit = {
    val currentStage = stalkStage
    for (changes <- database.changes(Map("feed" -> "longpoll", "timeout" -> Global.stalkersObsoleteDuration.toMillis.toString,
      "filter" -> "admin/with-stalkers", "stalked" -> stalkers.keys.map(_.toString).mkString(","), "since" -> fromSeq.toString)))
      self ! ('checkpoint, changes, currentStage)
  }

  // After having looked at the initial state of the stalkers, we look for changes
  // in a continuous way. A stalk stage number gets incremented every time.
  // We also look using long-polling for changes in the checkpoints and match it
  // with the stalk occurrence number in order not to handle old events and take
  // the changes into account immediately.

  val receive: Receive = {

    case ('initial, seq: Long, doc: Seq[(JsValue, JsObject)] @unchecked) =>
      doc.map(_._2).foreach(updateStalkees)
      launchStalkersChanges(seq)
      launchCheckpointChanges(seq)

    case ('checkpoint, doc: JsObject, stage: Long) =>
      if (stage == stalkStage) {
        val docs = (doc \ "results").as[Array[JsObject]]
        for (doc <- docs) {
          val bib = (doc \ "id").as[String].split("-")(2).toLong
          pipe(contestantInfo(bib).map(('ranking, bib, _))) to self
        }
        launchCheckpointChanges((doc \ "last_seq").as[Long])
      }

    case ('ranking, bib: Long, pos: (Int, Long, Int, Int) @unchecked) =>
      if (stalkers.contains(bib)) {
        position.get(bib) match {
          case Some(oldPos) if oldPos == pos =>
          case _ =>
            position += bib -> pos
            sendInfo(bib, pos)
        }
      }

    case json: JsObject =>
      if (updateStalkees((json \ "doc").as[JsObject])) {
        stalkStage += 1
        launchCheckpointChanges((json \ "seq").as[Long])
      }

  }

}
