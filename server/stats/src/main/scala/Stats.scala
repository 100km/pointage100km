import akka.actor.ActorSystem
import net.liftweb.json._
import net.rfc1149.canape._
import net.rfc1149.canape.implicits._
import scala.util.Random.nextInt

object Stats extends App {

  val options = new Options("stats")

  if (!options.parse(args))
    sys.exit(1)

  private implicit val system = ActorSystem()

  private implicit val formats = DefaultFormats

  val db: Database = new NioCouch().db("steenwerck100km")

  def update(checkpoint: Int ,bib: Int, race: Int) {
    val id = "checkpoint-" + checkpoint + "-" + bib
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
        val doc = Map("_id" -> id, "course" -> (1<< nextInt(3)), "nom" -> ("Bob_" + bibStr), "prenom" -> ("bobbie" + bibStr))
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
