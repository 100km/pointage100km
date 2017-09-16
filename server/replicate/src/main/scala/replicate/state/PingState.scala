package replicate.state

import akka.Done
import replicate.utils.Agent
import replicate.utils.Types.SiteId

import scala.concurrent.Future
import scalaz.@@

object PingState {

  import replicate.utils.Global.dispatcher

  val lastPings = Agent(Map[Int @@ SiteId, Long]())

  /**
   * Set the timestamp of the last ping seen for a given site.
   *
   * @param siteId the site id
   * @param timestamp the latest timestamp for this site
   * @return a future completed when the write has been performed
   */
  def setLastPing(siteId: Int @@ SiteId, timestamp: Long): Future[Done] =
    lastPings.alter(_ + (siteId → timestamp)).map(_ ⇒ Done)

  /**
   * Remove the timestamp associated to a site.
   *
   * @param siteId the site id
   * @return a future completed when the removal has been performed
   */
  def removePing(siteId: Int @@ SiteId): Future[Done] =
    lastPings.alter(_ - siteId).map(_ ⇒ Done)

  /**
   * Get the latest timestamp for a site.
   *
   * @param siteId the site id
   * @return a Future containing an Option with the last timestamp
   */
  def getLastPing(siteId: Int @@ SiteId): Future[Option[Long]] = lastPings.future.map(_.get(siteId))

}
