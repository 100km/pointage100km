package replicate

import akka.NotUsed
import akka.stream.scaladsl.{Keep, Sink, Source}
import net.rfc1149.canape.Couch
import play.api.libs.json.{JsObject, Json}
import replicate.scrutineer.Analyzer
import replicate.scrutineer.Analyzer.KeepPoint
import replicate.state.{CheckpointsState, PingState}
import replicate.utils.{Global, Infos}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object AnalyzerResult extends App {

  import Global.{dispatcher, flowMaterializer}
  implicit val system = Global.system

  def analyzeAnalyzer(raceId: Int, contestantId: Int): Future[JsObject] = {

    Global.log.info(s"Analyzing contestant $contestantId in race $raceId")

    var result: List[JsObject] = Nil
    var lastSignalledLap = 0
    var lastSignalledSiteId = 0
    var lastSignalledTimestamp = 0L
    var wrongNotifications = 0

    for (points ← CheckpointsState.timesFor(raceId, contestantId)) yield {
      val globalAnalysis = Analyzer.analyze(raceId, contestantId, points)
      val finalKeptPoints = globalAnalysis.checkpoints.collect { case k: KeepPoint ⇒ k.point }
      for (partial ← 1 to points.size) {
        val analysis = Analyzer.analyze(raceId, contestantId, points.take(partial))
        var wrongNotification = false
        val signalled = if (analysis.valid) analysis.checkpoints.reverse.collectFirst {
          case k: KeepPoint if k.point.timestamp > lastSignalledTimestamp && (k.lap > lastSignalledLap || (k.lap == lastSignalledLap && k.point.siteId > lastSignalledSiteId)) ⇒
            lastSignalledLap = k.lap
            lastSignalledSiteId = k.point.siteId
            lastSignalledTimestamp = k.point.timestamp
            if (!finalKeptPoints.contains(k.point)) {
              wrongNotification = true
              wrongNotifications += 1
            }
            k
        }
        else None
        result :+= Json.obj("point" → points(partial - 1), "wrong_notification" → wrongNotification, "signalled" → signalled,
          "analysis" → analysis)
      }
      Json.obj("race_id" → raceId, "bib" → contestantId,
        "wrong_notifications" → wrongNotifications,
        "steps" → Json.arr(result), "_id" → s"analysis-$raceId-$contestantId")
    }
  }

  private val infos: Infos = Json.parse(classOf[ClassLoader].getResourceAsStream("/infos.json")).as[Infos]
  Global.infos = Some(infos)
  (0 to 6).foreach(PingState.setLastPing(_, System.currentTimeMillis()))
  RaceUtils.installFullRace(pristine = true)

  val database = new Couch().db("analysis")
  val analyze = Source(1 to 3)
    .mapAsync(1) { raceId ⇒ CheckpointsState.contestants(raceId).map((raceId, _)) }
    .flatMapConcat { case (raceId, contestants) ⇒ Source(contestants.toVector.sorted).map((raceId, _)) }
    .mapAsync(4) { case (raceId, contestantId) ⇒ analyzeAnalyzer(raceId, contestantId) }
    .mapAsync(20)(database.insert(_))
    .toMat(Sink.ignore)(Keep.right)
  val done = database.delete().recover { case _ ⇒ NotUsed }.flatMap(_ ⇒ database.create()).flatMap(_ ⇒ analyze.run())
    .flatMap(_ ⇒ database.couch.releaseExternalResources())
    .flatMap(_ ⇒ system.terminate())

  Await.result(done, 1.minute)
}
