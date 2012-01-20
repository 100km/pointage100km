import akka.actor.{ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import com.typesafe.config.ConfigFactory
import dispatch._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import net.rfc1149.canape.helpers._

object Replicate {

  val config = Config("steenwerck.cfg")

  val system = ActorSystem("Replicator", ConfigFactory.load.getConfig("Replicator"))

  val pinnedProps = Props().withDispatcher("pinned-dispatcher")

  val log = Logging(system, "Replicate")

  def makeHttp(adapter: LoggingAdapter) =
    new Http {
      override def make_logger = new Logger {
	override def info(msg: String, items: Any*) = adapter.debug(msg.format(items: _*))
	override def warn(msg: String, items: Any*) = adapter.warning(msg.format(items: _*))
      }
    }


  val http = makeHttp(log)

  def createLocalInfo(db: Database, site: Int) = {
    val name = "_local/site-info"
    val doc = try {
      http(db(name))
    } catch {
	case StatusCode(404, _) => new JObject(Nil)
    }
    val newDoc = doc \ "site-id" match {
	case JNothing => doc ~ ("site-id" -> site)
	case _        => doc replace ("site-id" :: Nil, site)
    }
    http(db.insert(name, newDoc))
    touchMe(db)
  }

  def touchMe(db: Database) = {
    try {
      val touchMe = http(db("touch_me"))
      Http(db.insert(touchMe))
    } catch {
	case StatusCode(404, _) =>
	  Http(db.insert(new JObject(Nil), Some("touch_me")))
    }
  }

  def main(args: Array[String]) = {
    val site = Integer.parseInt(args(0))
    val localCouch = Couch("admin", "admin")
    val localDatabase = Database(localCouch, "steenwerck100km")
    val hubCouch = Couch(config.read[String]("master.host"),
			 config.read[Int]("master.port"),
			 config.read[String]("master.user"),
			 config.read[String]("master.password"))
    val hubDatabase = Database(hubCouch, config.read[String]("master.dbname"))

    try {
      Http(localDatabase.create)
    } catch {
	case StatusCode(status, _) =>
	  log.info("cannot create database: " + status)
    }

    createLocalInfo(localDatabase, site)

    system.actorOf(pinnedProps.withCreator(new ReplicationActor(localCouch, localDatabase, hubDatabase)),
		   "replication")
    system.actorOf(pinnedProps.withCreator(new ConflictsSolverActor(localDatabase)),
		   "conflictsSolver")
    system.actorOf(pinnedProps.withCreator(new IncompleteCheckpointsActor(localDatabase)),
		   "incompleteCheckpoints")

  }

}
