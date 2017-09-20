import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import net.rfc1149.canape.Couch.StatusError
import net.rfc1149.canape._
import org.specs2.mutable._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

// This requires a local standard CouchDB instance. The "canape-test-*" databases
// will be created, destroyed and worked into.

abstract class WithDbSpecification(dbSuffix: String) extends Specification {

  implicit val system = ActorSystem("canape-test")
  implicit val dispatcher = system.dispatcher
  implicit val timeout: Duration = (6, SECONDS)
  implicit val materializer = ActorMaterializer.create(system)

  val couch = new Couch

  trait freshDb extends BeforeAfter {

    val db = couch.db(s"canape-test-$dbSuffix-${UUID.randomUUID()}")
    var _waitEventually: List[Future[Any]] = Nil

    override def before = Await.ready(db.create(), timeout)

    override def after =
      try {
        Await.ready(Future.sequence(_waitEventually), timeout)
        Await.ready(db.delete(), timeout)
      } catch {
        case _: StatusError ⇒
      }

    def waitEventually[T](fs: Future[T]*): Unit = _waitEventually ++= fs
  }

  def waitForResult[T](f: Future[T]): T = Await.result(f, timeout)
  def waitForEnd[T](fs: Future[T]*): Unit = Await.ready(Future.sequence(fs), timeout)

  lazy val isCouchDB1 = waitForResult(couch.isCouchDB1)
  lazy val isCouchDB20 = waitForResult(couch.status().map(_.version.startsWith("2.0")))

  def pendingIfNotCouchDB1(msg: String) = if (!isCouchDB1) pending(s"[pending: $msg]")
  def pendingIfCouchDB20(msg: String) = if (isCouchDB20) pending(s"[pending: $msg]")

}
