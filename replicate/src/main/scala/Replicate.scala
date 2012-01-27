import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.rfc1149.canape._
import net.rfc1149.canape.helpers._

object Replicate extends App {

  import Global._

  private def createLocalInfo(db: Database, site: Int) = {
    val name = "_local/site-info"
    val doc: mapObject = try {
      db(name).execute
    } catch {
	case StatusCode(404, _) => Map()
    }
    val newDoc = doc + ("site-id" -> JInt(site))
    db.insert(name, newDoc).execute
    touchMe(db)
  }

  private def touchMe(db: Database) = {
    try {
      val touchMe = db("touch_me").execute
      db.insert(touchMe).execute
    } catch {
	case StatusCode(404, _) =>
	  db.insert("touch_me", Map()).execute
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
  val localCouch = new NioCouch(auth = Some("admin", "admin"))
  val localDatabase = localCouch.db("steenwerck100km")

  try {
    localDatabase.create.execute
  } catch {
    case StatusCode(412, _) =>
      log.info("database already exists")
    case t =>
      log.error("cannot create database: " + t)
  }

  createLocalInfo(localDatabase, site)

  {
    val hubCouch = new NioCouch(config.read[String]("master.host"),
				config.read[Int]("master.port"),
				Some(config.read[String]("master.user"),
				     config.read[String]("master.password")))
    val hubDatabase = hubCouch.db(config.read[String]("master.dbname"))
    createActor(new ReplicationActor(localCouch, localDatabase, hubDatabase), "replication")
  }
  createActor(new ConflictsSolverActor(localDatabase), "conflictsSolver")
  createActor(new IncompleteCheckpointsActor(localDatabase), "incompleteCheckpoints")

}
