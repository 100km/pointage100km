import akka.actor.{Actor, FSM}
import akka.util.Duration
import akka.util.duration._

abstract class PeriodicActor(period: Duration) extends Actor with FSM[PeriodicActor.State, Unit] {

  import PeriodicActor._

  def act()

  def handleMessage(message: Any)

  when(Idle, stateTimeout = period) {
    case Event(StateTimeout, _) =>
      goto(Processing)
  }

  when(Processing, stateTimeout = 0 seconds) {
    case Event(StateTimeout, _) =>
      act()
      goto(Idle)
  }

  whenUnhandled {
    case Event(m, _) =>
      handleMessage(m)
      stay()
  }

  startWith(Processing, ())
  initialize

}

object PeriodicActor {

  sealed trait State

  case object Idle extends State

  case object Processing extends State

}