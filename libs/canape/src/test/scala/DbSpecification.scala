import dispatch._
import net.rfc1149.canape._
import org.specs2.mutable._
import org.specs2.specification._

// This requires a local standard CouchDB instance. The "canape-test-*" databases
// will be created, destroyed and worked into. There must be an "admin"/"admin"
// account.

trait DbSpecification extends Specification with BeforeAfterExample {

  val dbSuffix: String

  lazy val couch = Couch("admin", "admin")
  lazy val db = couch.db("canape-test-" + dbSuffix)
  lazy val http = new Http with NoLogging

  override def before() =
    try {
      http(db.create())
    } catch {
	case StatusCode(412, _) =>
    }

  override def after() =
    try {
      http(db.delete())
    } catch {
	case StatusCode(404, _) =>
    }

}
