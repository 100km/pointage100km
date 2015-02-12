import akka.actor.ActorSystem
import net.liftweb.json._
import net.rfc1149.canape._
import net.rfc1149.canape.implicits._
import scopt.OptionParser
import scala.util.Random.nextInt

object Stats extends App {

  private case class Config(siteId: Int = -1, delay: Int = 0, count: Int = 100)

  private val parser = new scopt.OptionParser[Config]("stats") {
    opt[Int]('c', "count") action { (x, c) =>
      c.copy(count = x) } text("Number of checkpoints to insert (default: 100)")
    opt[Int]('d', "delay") action { (x, c) =>
      c.copy(delay = x) } text("Wait for delay in ms between updates (default: 0)")
    opt[Int]('s', "site_id") action { (x, c) =>
      c.copy(siteId = x) } text("Numerical id of the current site (default: random [0-2])")
    help("help") abbr("h") text("show this help")
  }

  private val options = parser.parse(args, Config()) getOrElse { sys.exit(1) }

  private val system = ActorSystem()
  private implicit val dispatcher = system.dispatcher

  private implicit val formats = DefaultFormats

  val db: Database = new NioCouch().db("steenwerck100km")

  def update(checkpoint: Int ,bib: Int, race: Int) {
    val id = "checkpoints-" + checkpoint + "-" + bib
    val r = db.update("bib_input", "add-checkpoint", id,
		      Map("ts" -> System.currentTimeMillis.toString)).execute()
    if (r \ "need_more" == JBool(true)) {
      val d = db(id).execute() + ("race_id" -> race) + ("bib" -> bib) + ("site_id" -> checkpoint)
      db.insert(d).execute()
    }
  }

  def recentCheckpointsMillis() = {
    val before = System.currentTimeMillis
    db.view("bib_input", "recent-checkpoints", List(("limit","10"))).execute()
    System.currentTimeMillis - before
  }

  try {
    for (bib <- 0 to 1000) {
      val bibStr = Integer.toString(bib)
      try {
        val id = "contestant-" + bibStr
        val doc = Map("_id" -> id, "race" -> (1<< nextInt(3)), "type" -> "contestant", "name" -> ("Bob_" + bibStr), "first_name" -> ("bobbie" + bibStr), "bib" -> bib, "birth" -> (1920 + nextInt(75)).toString(), "sex" -> (if (nextInt(1) == 0) "M" else "F"))
        db.insert(util.toJObject(doc)).execute()
        println("Inserted " + bibStr)
      } catch {
        case StatusCode(409, _) =>
          println("Bib info already exist for bib " + bibStr )
      }
    }
    for (i <- 1 to options.count) {
      val checkpoint = if (options.siteId != -1) options.siteId else nextInt(3)
      val bib = nextInt(1000)
      val race = nextInt(5)
      update(checkpoint, bib, race)
      Thread sleep options.delay
      println(i + " " + recentCheckpointsMillis)
    }
  } finally {
    db.couch.releaseExternalResources()
    system.shutdown()
  }

}
