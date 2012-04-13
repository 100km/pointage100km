import akka.actor.ActorSystem
import net.rfc1149.canape._
import org.specs2.mutable._
import org.specs2.specification._

// This requires a local standard CouchDB instance. The "canape-test-*" databases
// will be created, destroyed and worked into. There must be an "admin"/"admin"
// account.

trait DbSpecification extends Specification with BeforeAfterExample {

  implicit val dispatcher = ActorSystem().dispatcher

  val dbSuffix: String

  lazy val couch = new NioCouch(auth = Some("admin", "admin"))
  lazy val db = couch.db("canape-test-" + dbSuffix)

  override def before =
    try {
      db.create().execute()
    } catch {
	case _ =>
    }

  override def after =
    try {
      db.delete().execute()
    } catch {
	case _ =>
    }

  sequential

}
