package replicate.scrutineer

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import replicate.scrutineer.models.CheckpointStatus._
import replicate.scrutineer.models.{CheckpointAnalysis, CheckpointStatus, ContestantAnalysis}
import replicate.state.PingState
import replicate.state.RankingState.{Point, RankInformation}
import replicate.utils.Infos.RaceInfo
import replicate.utils.{Global, RaceUtils}

class Analyzer(contestantId: Int, raceInfo: RaceInfo, previousRank: Option[Int], rank: Option[Int]) {

  import Analyzer._

  private[this] val infos = Global.infos.get
  private[this] val pings = PingState.lastPings()
  private[this] val checkpoints = infos.checkpoints.size
  private[this] val startingReferencePoint = Point(checkpoints, raceInfo.startTime, -1)
  private[this] val startingPoint = EnrichedPoint(startingReferencePoint, 0, 0)

  private def analyze(points: Seq[Point]): ContestantAnalysis =
    ContestantAnalysis(contestantId, raceInfo.raceId, analyzePoints(points))

  private[this] def analyzePoints(original: Seq[Point]): Seq[CheckpointAnalysis] = {
    val enriched = enrichPoints(original)
    // Apply remove filters
    val (kept, removed) = enriched >> dropEarly >> dropLate >> dropSuspiciousStart >> dropWhile(findMinVarianceExtraPoint) >> dropLong
    val added = missingPoints(kept)
    val result = kept.map(p => p.addStatus(Ok(p.speed))) ++ removed ++ added
    result.sortBy(_.timestamp)
  }

  private[this] def dropEarly: KeepRemoveFilter = { points =>
    val early = points.takeWhile(_.timestamp < raceInfo.startTime).map(_.addStatus(TooEarly))
    (reenrichPoints(points.drop(early.size)), early)
  }

  private[this] def dropLate: KeepRemoveFilter = { points =>
    val kept = points.filter(_.timestamp <= raceInfo.endTime)
    val late = points.drop(kept.size).map(_.addStatus(TooLate))
    (kept, late)
  }

  private[this] def dropLong: KeepRemoveFilter = { points =>
    val kept = points.filter(_.lap <= raceInfo.laps)
    (kept, points.drop(kept.size).map(p => p.addStatus(TooLong)))
  }

  // Speeds exceeding the maximum allowed speed at the start cannot be a mistake due to
  // a later checkpoint. We can remove those points to work on the rest.
  private[this] def dropSuspiciousStart: KeepRemoveFilter = { points =>
    val suspicious = points.takeWhile(_.speed > maxSpeed).map(p => p.addStatus(Suspicious(p.speed)))
    (reenrichPoints(points.drop(suspicious.size)), suspicious)
  }

  private[this] def missingPoints(points: Seq[EnrichedPoint]): Seq[CheckpointAnalysis] = {
    (startingPoint +: points).sliding(2).flatMap { case Seq(before, after) =>
        for (index <- toIndex(before) + 1 until toIndex(after)) yield intermediatePoint(before, after, index)
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

  private[this] def intermediatePoint(before: EnrichedPoint, after: EnrichedPoint, index: Int): CheckpointAnalysis = {
    assert(index >= 0, s"index must be positive, currently $index")
    val (siteId, lap) = fromIndex(index)
    val distance = infos.distance(siteId, lap)
    val distanceRatio = distance / (after.distance - before.distance)
    val timestamp = (before.timestamp + (after.timestamp - before.timestamp) * distanceRatio).round
    val point = EnrichedPoint(Point(siteId, timestamp, lap), distance, after.speed)
    val lastPing = pings.getOrElse(siteId, 0L)
    if (lastPing < after.timestamp)
      point.addStatus(Down)
    else
      point.addStatus(Missing)
  }

  private[this] def dropWhile(f: Seq[EnrichedPoint] => Option[EnrichedPoint]): KeepRemoveFilter = { original =>
    var kept = original
    var extra = Seq[EnrichedPoint]()
    var toRemove = f(kept)
    while (toRemove.isDefined) {
      val removed = toRemove.get
      extra :+= removed
      val oldKept = kept
      kept = reenrichPoints(kept.filterNot(_.eq(removed)))
      toRemove = f(kept)
    }
    (kept, extra.map(p => p.addStatus(Suspicious(p.speed))))
  }

  private[this] def findMinVarianceExtraPoint(points: Seq[EnrichedPoint]): Option[EnrichedPoint] = {
    // Compute the points at either end of a segment where the speed is out of range
    val extra = (startingPoint +: points).sliding(2).filter(_.last.speed > maxSpeed).flatten.filterNot(_.eq(startingPoint)).toSet
    // For every of those points, compute the speed variance with this point removed
    if (extra.isEmpty)
      None
    else
      Some(extra.toVector.map(p => (p, speedVariance(reenrichPoints(points.filterNot(_.eq(p)))))).minBy(_._2)._1)
  }

  /**
    * Enrich the given points with distance and speed information.
    *
    * @param points the points
    * @return the enriched points with distance and speed
    */
  private[this] def addDistanceAndSpeed(points: Seq[Point]): Seq[EnrichedPoint] = {
    var previous = startingPoint
    points map { point =>
      assert(point != startingReferencePoint)
      val distance = infos.distance(point.siteId, point.lap)
      val speed = speedBetween(previous.distance, distance, previous.timestamp, point.timestamp)
      val newPoint = EnrichedPoint(point, distance, speed)
      previous = newPoint
      newPoint
    }

  }

  /**
    * Recompute the laps and enrich the points with distance and speed information
    *
    * @param points the original points
    * @return the enriched points with updated lap, distance, and speed information
    */
  private[this] def enrichPoints(points: Seq[Point]): Seq[EnrichedPoint] =
    addDistanceAndSpeed(recomputeLaps(points))

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

  def analyze(contestantId: Int, raceId: Int, rankInfo: RankInformation): ContestantAnalysis = {
    val raceInfo = Global.infos.get.races(raceId)
    val (previousRank, rank, points) = RankInformation.unapply(rankInfo).get
    val analyzer = new Analyzer(contestantId, raceInfo, previousRank, rank)
    analyzer.analyze(points)
  }

  def recomputeLaps(points: Seq[Point]): Seq[Point] =
    RaceUtils.addLaps(points.map(point => (point.siteId, point.timestamp)))

  case class EnrichedPoint(point: Point, distance: Double, speed: Double) {
    def siteId = point.siteId
    def lap = point.lap
    def timestamp = point.timestamp
    assert(timestamp >= 0, s"timestamp must be non-negative, currently $timestamp")
  }

  def speedBetween(startDistance: Double, endDistance: Double, startTimestamp: Long, endTimestamp: Long): Double =
    (endDistance - startDistance) * 3600 * 1000 / (endTimestamp - startTimestamp)

  def speedBetween(start: EnrichedPoint, end: EnrichedPoint): Double =
    speedBetween(start.distance, end.distance, start.timestamp, end.timestamp)

  def sq(a: Double): Double = a * a

  type KeptRemoved = (Seq[EnrichedPoint], Seq[CheckpointAnalysis])
  type KeepRemoveFilter = Seq[EnrichedPoint] => KeptRemoved

  implicit class Check1(data: Seq[EnrichedPoint]) {
    def >>(f: KeepRemoveFilter): KeptRemoved = f(data)
  }

  private implicit class Check2(data: KeptRemoved) {
    def >>(f: KeepRemoveFilter): KeptRemoved = {
      val (kept, removed) = f(data._1)
      (kept, data._2 ++ removed)
    }
  }

  private implicit class AddStatus(p: EnrichedPoint) {
    def addStatus(status: CheckpointStatus): CheckpointAnalysis =
      CheckpointAnalysis(p.siteId, p.lap, p.distance, p.timestamp, status)
  }

  private def speedVariance(points: Seq[EnrichedPoint]): Double = {
    assert(points.nonEmpty, "points cannot be empty")
    val speeds = points.map(_.speed)
    val mean = speeds.sum / speeds.size
    speeds.fold(0.0)((a, e) => a + math.pow(e - mean, 2)) / speeds.size
  }

}
