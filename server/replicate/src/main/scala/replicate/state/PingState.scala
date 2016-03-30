package replicate.state

import akka.NotUsed
import akka.agent.Agent

import scala.concurrent.Future

object PingState {

  import replicate.utils.Global.dispatcher

  val lastPings: Agent[Map[Int, Long]] = Agent(Map())

  /**
   * Set the timestamp of the last ping seen for a given site.
   *
   * @param siteId the site id
   * @param timestamp the latest timestamp for this site
   * @return a future completed when the write has been performed
   */
  def setLastPing(siteId: Int, timestamp: Long): Future[NotUsed] =
    lastPings.alter(_ + (siteId → timestamp)).map(_ ⇒ NotUsed)

  /**
   * Remove the timestamp associated to a site.
   *
   * @param siteId the site id
   * @return a future completed when the removal has been performed
   */
  def removePing(siteId: Int): Future[NotUsed] =
    lastPings.alter(_ - siteId).map(_ ⇒ NotUsed)

  /**
   * Get the latest timestamp for a site.
   *
   * @param siteId the site id
   * @return a Future containing an Option with the last timestamp
   */
  def getLastPing(siteId: Int): Future[Option[Long]] = lastPings.future.map(_.get(siteId))

  /**
   * Get the latest timestamp for a time without waiting for the current writes to be performed.
   *
   * @param siteId the site id
   * @return an Option with the last timestamp
   */
  def getLastPingImmediate(siteId: Int): Option[Long] = lastPings().get(siteId)

}
