import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import net.rfc1149.canape._
import net.rfc1149.canape.implicits._
import play.api.libs.json._

import scala.concurrent.duration._
import scala.util.Random._

object Stats extends App {

  private case class Config(siteId: Int = -1, delay: Int = 0, count: Int = 100)

  private val parser = new scopt.OptionParser[Config]("stats") {
    opt[Int]('c', "count") action { (x, c) =>
      c.copy(count = x)
    } text "Number of checkpoints to insert (default: 100)"
    opt[Int]('d', "delay") action { (x, c) =>
      c.copy(delay = x)
    } text "Wait for delay in ms between updates (default: 0)"
    opt[Int]('s', "site_id") action { (x, c) =>
      c.copy(siteId = x)
    } text "Numerical id of the current site (default: random [0-2])"
    help("help") abbr "h" text "show this help"
    override val showUsageOnError = true
  }

  private val options = parser.parse(args, Config()) getOrElse { sys.exit(1) }

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()
  private implicit val dispatcher = system.dispatcher

  private implicit val timeout: Duration = (5, SECONDS)

  val db: Database = steenwerck.localCouch().db(steenwerck.localDbName)

  def update(checkpoint: Int, bib: Int, race: Int): Unit = {
    val id = "checkpoints-" + checkpoint + "-" + bib
    val r = db.updateForm("bib_input", "add-checkpoint", id,
                                                         Map("ts" -> System.currentTimeMillis.toString), keepBody = true).flatMap(Couch.checkResponse[JsObject]).execute()
    if ((r \ "need_more").asOpt[Boolean].getOrElse(false)) {
      val d = db(id).execute() ++ Json.obj("race_id" -> race, "bib" -> bib, "site_id" -> checkpoint)
      db.insert(d).execute()
    }
  }

  def recentCheckpointsMillis() = {
    val before = System.currentTimeMillis
    db.view[JsValue, JsValue]("bib_input", "recent-checkpoints", List(("limit", "10"))).execute()
    System.currentTimeMillis - before
  }

  if (!steenwerck.testsAllowed(db).execute()) {
    println("The current database does not allow tests")
    sys.exit(1)
  }

  try {
    for (bib <- 1 to 1000) {
      val bibStr = Integer.toString(bib)
      try {
        val id = "contestant-" + bibStr
        val doc = Json.obj("_id" -> id, "race" -> (1 << nextInt(3)), "type" -> "contestant", "name" -> s"Bob_$bibStr",
          "first_name" -> s"bobbie$bibStr", "bib" -> bib, "birth" -> (1920 + nextInt(75)).toString(),
          "sex" -> (if (nextBoolean) "M" else "F"), "stalkers" -> List[String]())
        db.insert(doc).execute()
        println("Inserted " + bibStr)
      } catch {
        case Couch.StatusError(409, _, _) =>
          println("Bib info already exist for bib " + bibStr)
      }
    }
    for (i <- 1 to options.count) {
      val checkpoint = if (options.siteId != -1) options.siteId else nextInt(3)
      val bib = nextInt(1000)
      val race = nextInt(5)
      update(checkpoint, bib, race)
      Thread sleep options.delay
      println(s"$i $recentCheckpointsMillis")
    }
  } finally {
    db.couch.releaseExternalResources().execute()
    system.terminate()
  }

}
