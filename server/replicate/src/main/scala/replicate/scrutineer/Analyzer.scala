package replicate.scrutineer

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.{JsObject, Json, Writes}
import replicate.models.CheckpointData
import replicate.state.CheckpointsState.Point
import replicate.state.{CheckpointsState, PingState}
import replicate.utils.FormatUtils._
import replicate.utils.Global
import replicate.utils.Infos.RaceInfo

class Analyzer(raceInfo: RaceInfo, contestantId: Int, originalPoints: Seq[Point]) {

  import Analyzer._

  private[this] val infos = Global.infos.get
  private[this] val pings = PingState.lastPings()
  private[this] val checkpoints = infos.checkpoints.size
  private[this] val startingPoint = EnrichedPoint(Point(checkpoints - 1, raceInfo.startTime), 0, 0, 0)

  private def analyze(points: Seq[Point]): ContestantAnalysis = {
    val analyzed = analyzeRace(points)
    val before = enrichPoints(points)
    val after = enrichPoints(analyzed.collect { case p: KeepPoint ⇒ p.point })
    ContestantAnalysis(contestantId, raceInfo.raceId, analyzeRace(points), before, after)
  }

  private[this] def analyzeRace(original: Seq[Point]): Seq[AnalyzedPoint] = {
    val points = analyzePoints(original)
    val initialAnomalies = points.count(_.isInstanceOf[Anomaly])
    var anomalies = initialAnomalies
    var currentBest = points

    // We will try different strategies to reduce the score.
    def retryWithout(removalMessage: String)(point: AnalyzedPoint) = {
      val reanalyzed = analyzeRace(original.filterNot(_ == point.point))
      val reanalyzedAnomalies = countAnomalies(reanalyzed) + 1
      if (reanalyzedAnomalies < anomalies) {
        currentBest = (RemovePoint(point.point, removalMessage) +: reanalyzed).sortBy(_.point.timestamp)
        anomalies = reanalyzedAnomalies
      }
    }

    // Try to remove points surrounded by missing ones.
    if (initialAnomalies >= surroundedByMissing)
      findSurroundedByMissing(points).foreach(retryWithout(s"Checkpoint surrounded by at least $surroundedByMissing missing checkpoints"))

    // Try to remove points which are at an extremity of a long strike of missing points.
    if (initialAnomalies >= maxConsecutiveMissing) {
      val candidates = findEnclosingMissing(points)
      candidates.foreach(retryWithout(s"Checkpoint enclosing at least $maxConsecutiveMissing missing checkpoints"))
    }

    // Return the best solution.
    currentBest
  }

  private[this] def findSurroundedByMissing(points: Seq[AnalyzedPoint]): Seq[AnalyzedPoint] = {
    points.filterNot(_.isInstanceOf[RemovePoint]).sliding(surroundedByMissing + 1)
      .filter(pts ⇒ pts.count(_.isInstanceOf[MissingPoint]) == surroundedByMissing &&
        pts.head.isInstanceOf[MissingPoint] && pts.last.isInstanceOf[MissingPoint])
      .flatMap(_.find(_.isInstanceOf[GenuinePoint])).toSeq
  }

  private[this] def findEnclosingMissing(points: Seq[AnalyzedPoint]): Seq[AnalyzedPoint] = {
    points.filterNot(_.isInstanceOf[RemovePoint]).sliding(maxConsecutiveMissing + 1)
      .filter(_.count(_.isInstanceOf[MissingPoint]) == maxConsecutiveMissing)
      .collect {
        case pts if pts.head.isInstanceOf[GenuinePoint] ⇒ pts.head
        case pts if pts.last.isInstanceOf[GenuinePoint] ⇒ pts.last
      }
      .toSeq.reverse // Favor end points in case of a night stop
  }

  /**
   * Return a sorted list of analyzed points.
   *
   * @param original the points to analyze
   * @return the result of the analysis with points added or removed
   */
  private[this] def analyzePoints(original: Seq[Point]): Seq[AnalyzedPoint] = {
    val enriched = enrichPoints(original)
    // Apply remove filters
    val (firstKept, firstRemoved) = (enriched, Seq()) >> dropEarly >> dropLate >> dropSuspiciousStart(absoluteMaxSpeed) >>
      dropWhile(findMinVarianceExtraPoint(absoluteMaxSpeed)) >> dropLong >> dropSuspiciousEnd(absoluteMaxSpeed)
    // We compute the acceptable speed as 200% more than the median speed excluding the first leg (as it might
    // be lower than in reality if the contestant started late).
    val maxSpeed = if (firstKept.size >= 5) absoluteMaxSpeed.min(medianSpeedFactor * median(firstKept.tail.map(_.speed))) else absoluteMaxSpeed
    val (kept, removed) = (
      if (maxSpeed < absoluteMaxSpeed)
        // Rerun the checks with the new maximum speed
        (firstKept, firstRemoved) >> dropSuspiciousStart(maxSpeed) >> dropWhile(findMinVarianceExtraPoint(maxSpeed))
      else
        (firstKept, firstRemoved)
    ) >> dropLong >> dropSuspiciousEnd(absoluteMaxSpeed)
    val added = missingPoints(kept)
    val points = kept.map(p ⇒ GenuinePoint(p.point, p.lap, p.distance, p.speed)) ++ removed ++ added
    points.sortBy(_.point.timestamp)
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
  private[this] def dropSuspiciousStart(maxSpeed: Double): KeepRemoveFilter = { points ⇒
    val suspicious = points.takeWhile(_.speed > maxSpeed).map(p ⇒ RemovePoint(p.point, s"${q(maxSpeed)} initial speed: ${formatSpeed(p.speed)}"))
    (reenrichPoints(points.drop(suspicious.size)), suspicious)
  }

  private[this] def dropSuspiciousEnd(maxSpeed: Double): KeepRemoveFilter = { points ⇒
    points.takeRight(3) match {
      case Seq(a, b, end) if b.speed <= maxSpeed && end.speed > maxSpeed ⇒
        val previousSpeed = speedBetween(a.distance, end.distance, a.timestamp, end.timestamp)
        if (previousSpeed > maxSpeed)
          (points.dropRight(1), Seq(RemovePoint(
            points.last.point,
            s"${q(maxSpeed)} speed on the last section (${formatSpeed(end.speed)}) and the two last sections (${formatSpeed(previousSpeed)})"
          )))
        else
          (points, Seq())
      case _ ⇒
        (points, Seq())
    }
  }

  private[this] def q(maxSpeed: Double) = if (maxSpeed < absoluteMaxSpeed) "Suspicious" else "Excessive"

  private[this] def missingPoints(points: Seq[EnrichedPoint]): Seq[ExtraPoint] = {
    (startingPoint +: points).sliding(2).flatMap {
      case Seq(before, after) ⇒
        for (index ← toIndex(before) + 1 until toIndex(after)) yield intermediatePoint(before, after, index)
      case _ ⇒
        // Less than 2 points
        Seq()
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
    val distanceRatio = (distance - before.distance) / (after.distance - before.distance)
    assert(distanceRatio > 0)
    val timestamp = (before.timestamp + (after.timestamp - before.timestamp) * distanceRatio).round
    assert(timestamp > before.timestamp)
    assert(timestamp < after.timestamp)
    val point = Point(siteId, timestamp)
    val lastPing = pings.get(siteId)
    if (lastPing.exists(_ >= after.timestamp - 2 * Global.pingTimeout.toMillis))
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

  private[this] def findMinVarianceExtraPoint(maxSpeed: Double)(points: Seq[EnrichedPoint]): Option[RemovePoint] = {
    // Compute the points at either end of a segment where the speed is out of range. The latest point
    // is never considered for removal unless it goes beyond the end of the race.
    val consideredPoints = if (points.lastOption.exists(_.lap > raceInfo.laps)) points else points.dropRight(1)
    val excessiveSpeedBefore = consideredPoints.filter(_.speed > maxSpeed).toSet
    val excessiveSpeedAfter = points.sliding(2).collect { case Seq(start, end) if end.speed > maxSpeed ⇒ start }.toSet
    val excessiveSpeed = excessiveSpeedBefore ++ excessiveSpeedAfter
    if (excessiveSpeed.isEmpty)
      None
    else {
      // Find the point whose removal will lead to the smallest variance
      val toRemove = excessiveSpeed.toVector.map(p ⇒ (p, speedVariance(reenrichPoints(points.filterNot(_.eq(p)))))).minBy(_._2)._1
      // We might need to find the point following the removed point
      val successor = points.sliding(2).collectFirst { case Seq(`toRemove`, next) ⇒ next }
      // Remove this point with a meaningful error message
      (excessiveSpeedBefore.contains(toRemove), excessiveSpeedAfter.contains(toRemove), successor) match {
        case (true, false, _) ⇒
          Some(RemovePoint(toRemove.point, s"${q(maxSpeed)} speed before this checkpoint: ${formatSpeed(toRemove.speed)}"))
        case (false, true, Some(next)) ⇒
          Some(RemovePoint(toRemove.point, s"${q(maxSpeed)} speed after this checkpoint: ${formatSpeed(next.speed)}"))
        case (true, true, Some(next)) ⇒
          Some(RemovePoint(toRemove.point, s"${q(maxSpeed)} speed around this checkpoint: ${formatSpeed(toRemove.speed)} before and ${formatSpeed(next.speed)} after"))
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
  private val absoluteMaxSpeed = config.as[Double]("max-acceptable-speed")
  private val medianSpeedFactor = config.as[Double]("median-speed-factor")
  private val maxAnomalies = config.as[Int]("max-anomalies")
  private val maxConsecutiveAnomalies = config.as[Int]("max-consecutive-anomalies")
  private val surroundedByMissing = config.as[Int]("surrounded-by-missing")
  private val maxConsecutiveMissing = config.as[Int]("max-consecutive-missing")

  def analyze(raceId: Int, contestantId: Int, points: Seq[Point]): ContestantAnalysis = {
    val raceInfo = Global.infos.get.races(raceId)
    val analyzer = new Analyzer(raceInfo, contestantId, points)
    val result = analyzer.analyze(points)
    result
  }

  def analyze(data: Seq[CheckpointData]): ContestantAnalysis = {
    // Conduct an analysis with the valid points only
    val sample = data.head
    val points = CheckpointsState.sortedTimestamps(data)
    val analysis = analyze(sample.raceId, sample.contestantId, points)
    // Enrich the analysis with information about the extra points
    val deletedPoints = data.flatMap(cpd ⇒ CheckpointsState.toPoints(cpd.siteId, cpd.deletedTimestamps).map(DeletedPoint))
    val inserted = data.flatMap(cpd ⇒ CheckpointsState.toPoints(cpd.siteId, cpd.insertedTimestamps)).toSet
    val newCheckpoints = deletedPoints ++ analysis.checkpoints.map {
      case p: GenuinePoint if inserted.contains(p.point) ⇒ ArtificialPoint(p)
      case p ⇒ p
    }
    analysis.copy(checkpoints = newCheckpoints.sortBy(_.point.timestamp))
  }

  case class EnrichedPoint(point: Point, lap: Int, distance: Double, speed: Double) {
    def siteId = point.siteId
    def timestamp = point.timestamp
    assert(timestamp >= 0, s"timestamp must be non-negative, currently $timestamp")
    override def toString = s"EnrichedPoint($point, $lap, ${formatDistance(distance)}, ${formatSpeed(speed)})"
  }

  case object EnrichedPoint {
    implicit val enrichedPointWrites: Writes[EnrichedPoint] = Writes { p ⇒
      Json.obj("site_id" → p.point.siteId, "time" → p.point.timestamp,
        "lap" → p.lap, "distance" → p.distance, "speed" → p.speed)
    }
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

  sealed trait KeepPoint extends AnalyzedPoint with WithCheckpointInfo

  sealed trait Anomaly extends AnalyzedPoint

  sealed trait ExtraPoint extends KeepPoint with Anomaly

  final case class GenuinePoint(point: Point, lap: Int, distance: Double, speed: Double) extends KeepPoint {
    override def toJson = super.toJson ++ Json.obj("type" → "genuine")
    override def toString = s"CorrectPoint($point, $lap, ${formatDistance(distance)}, ${formatSpeed(speed)})"
  }

  final case class ArtificialPoint(point: Point, lap: Int, distance: Double, speed: Double) extends KeepPoint {
    override def toJson = super.toJson ++ Json.obj("type" → "artificial")
    override def toString = s"ArtificialPoint($point, $lap, ${formatDistance(distance)}, ${formatSpeed(speed)})"
  }

  object ArtificialPoint {
    def apply(point: GenuinePoint): ArtificialPoint = ArtificialPoint(point.point, point.lap, point.distance, point.speed)
  }

  final case class DeletedPoint(point: Point) extends AnalyzedPoint {
    override def toJson = super.toJson ++ Json.obj("type" → "deleted")
  }

  final case class RemovePoint(point: Point, reason: String) extends AnalyzedPoint with Anomaly {
    override def toJson = super.toJson ++ Json.obj("type" → "remove", "reason" → reason, "action" → "remove")
  }

  final case class MissingPoint(point: Point, lap: Int, distance: Double, speed: Double) extends ExtraPoint {
    override def toJson = super.toJson ++ Json.obj("type" → "missing", "action" → "add")
    override def toString = s"MissingPoint($point, $lap, ${formatDistance(distance)}, ${formatSpeed(speed)})"
  }

  final case class DownPoint(point: Point, lap: Int, distance: Double, speed: Double, lastPing: Option[Long])
      extends ExtraPoint {
    private def reason = lastPing.fold("Site has never been up")(downSince ⇒ s"Site is down since ${formatDate(downSince)}")
    override def toJson = super.toJson ++ Json.obj("type" → "down", "reason" → reason)
    override def toString = s"DownPoint($point, $lap, ${formatDistance(distance)}, ${formatSpeed(speed)}, $reason)"
  }

  case class ContestantAnalysis(contestantId: Int, raceId: Int, checkpoints: Seq[AnalyzedPoint],
      before: Seq[EnrichedPoint], after: Seq[EnrichedPoint]) {
    val anomalies = countAnomalies(checkpoints)
    val consecutiveAnomalies = countConsecutiveAnomalies(checkpoints)
    val valid = anomalies < maxAnomalies && consecutiveAnomalies < maxConsecutiveAnomalies
    val isOk = anomalies == 0
    val id = s"analysis-$contestantId"

    def bestPoint: Option[KeepPoint] = if (valid) checkpoints.reverse.collectFirst { case p: KeepPoint ⇒ p } else None
  }

  private def countAnomalies(checkpoints: Seq[AnalyzedPoint]) = checkpoints.count(_.isInstanceOf[Anomaly])

  private def countConsecutiveAnomalies(checkpoints: Seq[AnalyzedPoint]) =
    checkpoints.scanLeft(0) {
      case (encountered, point) ⇒
        if (point.isInstanceOf[Anomaly])
          encountered + 1
        else
          0
    }.max

  object ContestantAnalysis {
    implicit val contestantAnalysisWrites: Writes[ContestantAnalysis] = Writes { analysis ⇒
      Json.obj("type" → "analysis", "bib" → analysis.contestantId, "race_id" → analysis.raceId,
        "valid" → analysis.valid, "anomalies" → analysis.anomalies,
        "checkpoints" → analysis.checkpoints, "before" → analysis.before, "after" → analysis.after,
        "_id" → analysis.id)
    }
  }

  def speedBetween(startDistance: Double, endDistance: Double, startTimestamp: Long, endTimestamp: Long): Double =
    (endDistance - startDistance) * 3600 * 1000 / (endTimestamp - startTimestamp)

  def speedBetween(start: EnrichedPoint, end: EnrichedPoint): Double =
    speedBetween(start.distance, end.distance, start.timestamp, end.timestamp)

  def sq(a: Double): Double = a * a

  type KeptRemoved = (Seq[EnrichedPoint], Seq[RemovePoint])
  type KeepRemoveFilter = Seq[EnrichedPoint] ⇒ KeptRemoved

  private implicit class Check(data: KeptRemoved) {
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

  def median(data: Seq[Double]): Double = {
    val size = data.size
    val halfSize = size / 2
    assert(size > 0)
    val sorted = data.sorted
    if (size % 2 == 1)
      sorted(halfSize)
    else {
      val (a, b) = (sorted(halfSize - 1), sorted(halfSize))
      (a + b) / 2
    }
  }

}
