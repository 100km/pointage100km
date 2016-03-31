package replicate.scrutineer

import java.util.Calendar

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.{JsObject, Json, Writes}
import replicate.state.CheckpointsState.Point
import replicate.state.PingState
import replicate.utils.Global
import replicate.utils.Infos.RaceInfo

class Analyzer(raceInfo: RaceInfo, contestantId: Int, originalPoints: Seq[Point]) {

  import Analyzer._

  private[this] val infos = Global.infos.get
  private[this] val pings = PingState.lastPings()
  private[this] val checkpoints = infos.checkpoints.size
  private[this] val startingPoint = EnrichedPoint(Point(checkpoints - 1, raceInfo.startTime), -1, 0, 0)

  private def analyze(points: Seq[Point]): ContestantAnalysis =
    ContestantAnalysis(contestantId, raceInfo.raceId, analyzePoints(points))

  private[this] def analyzePoints(original: Seq[Point]): Seq[AnalyzedPoint] = {
    val enriched = enrichPoints(original)
    // Apply remove filters
    val (kept, removed) = enriched >> dropEarly >> dropLate >> dropSuspiciousStart >>
      dropWhile(findMinVarianceExtraPoint) >> dropLong >> dropSuspiciousEnd
    val added = missingPoints(kept)
    val result = kept.map(p ⇒ CorrectPoint(p.point, p.lap, p.distance, p.speed)) ++ removed ++ added
    result.sortBy(_.point.timestamp)
  }

  private[this] def dropEarly: KeepRemoveFilter = { points ⇒
    val early = points.takeWhile(_.timestamp < raceInfo.startTime).map(p ⇒ RemovePoint(p.point, "Race has not started yet"))
    (reenrichPoints(points.drop(early.size)), early)
  }

  private[this] def dropLate: KeepRemoveFilter = { points ⇒
    val kept = points.filter(_.timestamp <= raceInfo.endTime)
    val late = points.drop(kept.size).map(p ⇒ RemovePoint(p.point, "Race is already finished"))
    (kept, late)
  }

  private[this] def dropLong: KeepRemoveFilter = { points ⇒
    val kept = points.filter(_.lap <= raceInfo.laps)
    (kept, points.drop(kept.size).map(p ⇒ RemovePoint(p.point, s"Number of laps in this race: ${raceInfo.laps}")))
  }

  // Speeds exceeding the maximum allowed speed at the start cannot be a mistake due to
  // a later checkpoint. We can remove those points to work on the rest.
  private[this] def dropSuspiciousStart: KeepRemoveFilter = { points ⇒
    val suspicious = points.takeWhile(_.speed > maxSpeed).map(p ⇒ RemovePoint(p.point, s"Suspicious initial speed: ${formatSpeed(p.speed)}"))
    (reenrichPoints(points.drop(suspicious.size)), suspicious)
  }

  private[this] def dropSuspiciousEnd: KeepRemoveFilter = { points ⇒
    points.takeRight(3) match {
      case Seq(a, b, end) if b.speed <= maxSpeed && end.speed > maxSpeed ⇒
        val previousSpeed = speedBetween(a.distance, end.distance, a.timestamp, end.timestamp)
        if (previousSpeed > maxSpeed)
          (points.dropRight(1), Seq(RemovePoint(
            points.last.point,
            s"Excessive speed on the last section (${formatSpeed(end.speed)}) and the two last sections (${formatSpeed(previousSpeed)})"
          )))
        else
          (points, Seq())
      case _ ⇒
        (points, Seq())
    }
  }

  private[this] def missingPoints(points: Seq[EnrichedPoint]): Seq[ExtraPoint] = {
    (startingPoint +: points).sliding(2).flatMap {
      case Seq(before, after) ⇒
        for (index ← toIndex(before) + 1 until toIndex(after)) yield intermediatePoint(before, after, index)
    }.toSeq
  }

  /**
   * Return the index (0 for the first checkpoint) corresponding to the siteId and lap at the
   * given point.
   *
   * @param point a point
   * @return the corresponding index
   */
  private[this] def toIndex(point: EnrichedPoint): Int = {
    if (point.eq(startingPoint))
      -1
    else {
      assert(point.siteId >= 0, "siteId must not be negative")
      assert(point.lap > 0, "lap must be positive")
      (point.lap - 1) * checkpoints + point.siteId
    }
  }

  /**
   * Return the siteId and lap corresponding to the given index (0 is the first index).
   *
   * @param index an index
   * @return the corresponding siteId and lap
   */
  private[this] def fromIndex(index: Int): (Int, Int) = {
    assert(index >= 0, "index must not be negative")
    (index % checkpoints, index / checkpoints + 1)
  }

  private[this] def intermediatePoint(before: EnrichedPoint, after: EnrichedPoint, index: Int): ExtraPoint = {
    assert(index >= 0, s"index must be positive, currently $index")
    val (siteId, lap) = fromIndex(index)
    val distance = infos.distance(siteId, lap)
    val distanceRatio = distance / (after.distance - before.distance)
    val timestamp = (before.timestamp + (after.timestamp - before.timestamp) * distanceRatio).round
    val point = Point(siteId, timestamp)
    val lastPing = pings.get(siteId)
    if (lastPing.exists(_ >= after.timestamp))
      MissingPoint(point, lap, distance, after.speed)
    else
      DownPoint(point, lap, distance, after.speed, lastPing)
  }

  private[this] def dropWhile(f: Seq[EnrichedPoint] ⇒ Option[RemovePoint]): KeepRemoveFilter = { original ⇒
    var kept = original
    var extra = Seq[RemovePoint]()
    var toRemove = f(kept)
    while (toRemove.isDefined) {
      val removed = toRemove.get
      extra :+= removed
      val oldKept = kept
      kept = reenrichPoints(kept.filterNot(_.point == removed.point))
      toRemove = f(kept)
    }
    (kept, extra)
  }

  private[this] def findMinVarianceExtraPoint(points: Seq[EnrichedPoint]): Option[RemovePoint] = {
    // Compute the points at either end of a segment where the speed is out of range. The latest point
    // is never considered for removal.
    val excessiveSpeedBefore = points.dropRight(1).filter(_.speed > maxSpeed).toSet
    val excessiveSpeedAfter = points.sliding(2).collect { case Seq(start, end) if end.speed > maxSpeed ⇒ start }.toSet
    val excessiveSpeed = excessiveSpeedBefore ++ excessiveSpeedAfter
    if (excessiveSpeed.isEmpty)
      None
    else {
      // Find the point whose removal will lead to the smallest variance
      val toRemove = excessiveSpeed.toVector.map(p ⇒ (p, speedVariance(reenrichPoints(points.filterNot(_.eq(p)))))).minBy(_._2)._1
      // We might need to find the point following the removed point
      val successor = points.sliding(2).collectFirst { case Seq(`toRemove`, next) ⇒ next }.get
      // Remove this point with a meaningful error message
      (excessiveSpeedBefore.contains(toRemove), excessiveSpeedAfter.contains(toRemove), successor) match {
        case (true, false, _) ⇒
          Some(RemovePoint(toRemove.point, s"Excessive speed before this checkpoint: ${formatSpeed(toRemove.speed)}"))
        case (false, true, next) ⇒
          Some(RemovePoint(toRemove.point, s"Excessive speed after this checkpoint: ${formatSpeed(next.speed)}"))
        case (true, true, next) ⇒
          Some(RemovePoint(toRemove.point, s"Excessive speed around this checkpoint: ${formatSpeed(toRemove.speed)} and ${formatSpeed(next.speed)}"))
        case _ ⇒
          // This can never happen
          None
      }
    }
  }

  /**
   * Recompute the laps and enrich the points with distance and speed information
   *
   * @param points the original points
   * @return the enriched points with updated lap, distance, and speed information
   */
  private[this] def enrichPoints(points: Seq[Point]): Seq[EnrichedPoint] = {
    points.scanLeft((startingPoint.point, 0, 0.0, 0.0)) {
      case ((prevPoint, prevLap, prevDistance, prevSpeed), point@Point(siteId, timestamp)) ⇒
        assert(prevPoint.timestamp < timestamp)
        val lap = if (siteId <= prevPoint.siteId) prevLap + 1 else prevLap
        val distance = infos.distance(siteId, lap)
        val speed = speedBetween(prevDistance, distance, prevPoint.timestamp, timestamp)
        (point, lap, distance, speed)
    }.tail.map { case (point, lap, distance, speed) ⇒ EnrichedPoint(point, lap, distance, speed) }
  }

  /**
   * Recompute the laps, distance, and speed information for a (possibly modified) set of points
   *
   * @param points the original points
   * @return the points with updated information
   */
  private[this] def reenrichPoints(points: Seq[EnrichedPoint]): Seq[EnrichedPoint] =
    enrichPoints(points.map(_.point))

}

object Analyzer {

  private val config = Global.replicateConfig.as[Config]("analyzer")
  private val maxSpeed = config.as[Double]("max-acceptable-speed")

  def analyze(raceId: Int, contestantId: Int, points: Seq[Point]): ContestantAnalysis = {
    val raceInfo = Global.infos.get.races(raceId)
    val analyzer = new Analyzer(raceInfo, contestantId, points)
    analyzer.analyze(points)
  }

  case class EnrichedPoint(point: Point, lap: Int, distance: Double, speed: Double) {
    def siteId = point.siteId
    def timestamp = point.timestamp
    assert(timestamp >= 0, s"timestamp must be non-negative, currently $timestamp")
  }

  sealed trait AnalyzedPoint {
    def point: Point
    def toJson: JsObject = Json.obj("site_id" → point.siteId, "time" → point.timestamp)
  }

  object AnalyzedPoint {
    implicit val analyzedPointWrites: Writes[AnalyzedPoint] = Writes(_.toJson)
  }

  sealed trait WithCheckpointInfo extends AnalyzedPoint {
    def lap: Int
    def distance: Double
    def speed: Double
    override def toJson = super.toJson ++ Json.obj("lap" → lap, "distance" → distance, "speed" → speed)
  }

  sealed trait ExtraPoint extends AnalyzedPoint

  final case class CorrectPoint(point: Point, lap: Int, distance: Double, speed: Double) extends WithCheckpointInfo {
    override def toJson = super.toJson ++ Json.obj("type" → "correct")
  }

  final case class RemovePoint(point: Point, reason: String) extends AnalyzedPoint {
    override def toJson = super.toJson ++ Json.obj("type" → "remove", "reason" → reason, "action" → "remove")
  }

  final case class MissingPoint(point: Point, lap: Int, distance: Double, speed: Double) extends WithCheckpointInfo with ExtraPoint {
    override def toJson = super.toJson ++ Json.obj("type" → "missing", "action" → "add")
  }

  final case class DownPoint(point: Point, lap: Int, distance: Double, speed: Double, lastPing: Option[Long])
      extends WithCheckpointInfo with ExtraPoint {
    private def reason = lastPing.fold("Site has never been up")(downSince ⇒ s"Site is down since ${formatDate(downSince)}")
    override def toJson = super.toJson ++ Json.obj("type" → "down", "reason" → reason)
  }

  case class ContestantAnalysis(contestantId: Int, raceId: Int, checkpoints: Seq[AnalyzedPoint]) {
    def isOk = checkpoints.forall(_.isInstanceOf[CorrectPoint])
    def id = s"problem-$contestantId"
  }

  object ContestantAnalysis {
    implicit val contestantAnalysisWrites: Writes[ContestantAnalysis] = Writes { analysis ⇒
      Json.obj("type" → "problem", "bib" → analysis.contestantId, "race_id" → analysis.raceId,
        "checkpoints" → analysis.checkpoints)
    }
  }

  private def formatDate(timestamp: Long) = {
    val calendar = Calendar.getInstance()
    calendar.setTimeInMillis(timestamp)
    "%d:%02d".format(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
  }

  private def formatSpeed(speed: Double) = "%.2f km/h".format(speed)

  def speedBetween(startDistance: Double, endDistance: Double, startTimestamp: Long, endTimestamp: Long): Double =
    (endDistance - startDistance) * 3600 * 1000 / (endTimestamp - startTimestamp)

  def speedBetween(start: EnrichedPoint, end: EnrichedPoint): Double =
    speedBetween(start.distance, end.distance, start.timestamp, end.timestamp)

  def sq(a: Double): Double = a * a

  type KeptRemoved = (Seq[EnrichedPoint], Seq[RemovePoint])
  type KeepRemoveFilter = Seq[EnrichedPoint] ⇒ KeptRemoved

  implicit class Check1(data: Seq[EnrichedPoint]) {
    def >>(f: KeepRemoveFilter): KeptRemoved = f(data)
  }

  private implicit class Check2(data: KeptRemoved) {
    def >>(f: KeepRemoveFilter): KeptRemoved = {
      val (kept, removed) = f(data._1)
      (kept, removed ++ data._2)
    }
  }

  private def speedVariance(points: Seq[EnrichedPoint]): Double = {
    assert(points.nonEmpty, "points cannot be empty")
    val speeds = points.map(_.speed)
    val mean = speeds.sum / speeds.size
    speeds.fold(0.0)((a, e) ⇒ a + math.pow(e - mean, 2)) / speeds.size
  }

}
