import dispatch._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import net.rfc1149.canape.helpers._

object Replicate extends App {

  import Global._

  private val http = makeHttp(log)

  private def createLocalInfo(db: Database, site: Int) = {
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

  private def touchMe(db: Database) = {
    try {
      val touchMe = http(db("touch_me"))
      http(db.insert(touchMe))
    } catch {
	case StatusCode(404, _) =>
	  http(db.insert(new JObject(Nil), Some("touch_me")))
    }
  }

  private def usage() = {
    println("Usage: replicate N")
    println("   N: number of the site this program runs on")
    sys.exit(1)
  }

  if (args.size != 1)
    usage()

  val site = Integer.parseInt(args(0))
  val localCouch = Couch("admin", "admin")
  val localDatabase = Database(localCouch, "steenwerck100km")
  val hubCouch = Couch(config.read[String]("master.host"),
		       config.read[Int]("master.port"),
		       config.read[String]("master.user"),
		       config.read[String]("master.password"))
  val hubDatabase = Database(hubCouch, config.read[String]("master.dbname"))

  try {
    http(localDatabase.create)
  } catch {
    case StatusCode(status, _) =>
      log.info("cannot create database: " + status)
  }

  createLocalInfo(localDatabase, site)

  createActor(new ReplicationActor(localCouch, localDatabase, hubDatabase), "replication")
  createActor(new ConflictsSolverActor(localDatabase), "conflictsSolver")
  createActor(new IncompleteCheckpointsActor(localDatabase), "incompleteCheckpoints")

}
