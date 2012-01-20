import akka.actor.{Actor, Cancellable}
import akka.util.Duration
import akka.util.duration._

trait PeriodicActor extends Actor {

  protected val period: Duration

  private var wakeupTimer: Cancellable = _

  override def preStart() = {
    super.preStart()
    wakeupTimer = context.system.scheduler.schedule(0 seconds, period, self, 'wakeup)
  }

  override def postStop() = {
    wakeupTimer.cancel()
    super.postStop()
  }

  override def receive = {
      case 'wakeup => periodic()
  }

  def periodic(): Unit

}
