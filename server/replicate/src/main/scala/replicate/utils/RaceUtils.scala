package replicate.utils

import replicate.state.RankingState.Point

object RaceUtils {

  def addLaps(sitesAndTimestamps: Seq[(Int, Long)]): Seq[Point] = {
    var currentLap = 0
    var latestSiteId = Int.MaxValue
    for ((siteId, timestamp) <- sitesAndTimestamps) yield {
      if (siteId <= latestSiteId)
        currentLap += 1
      latestSiteId = siteId
      Point(siteId, timestamp, currentLap)
    }
  }

}
