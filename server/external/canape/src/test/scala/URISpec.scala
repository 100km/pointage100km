import akka.actor.ActorSystem
import net.rfc1149.canape._
import org.specs2.mutable._
import play.api.libs.json.{JsValue, Json}

class URISpec extends Specification {

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val cunauth = new Couch("db.example.com", 5984)
  val cauth = new Couch("db.example.com", 5984, Some(("admin", "xyzzy")))
  val scunauth = new Couch("db.example.com", 5984, secure = true)
  val scauth = new Couch("db.example.com", 5984, Some(("admin", "xyzzy")), secure = true)

  "The 'Couch' class" should {
    "return the right unauthentified URI" in {
      cunauth.uri.toString mustEqual "http://db.example.com:5984"
    }

    "return the right authentified URI" in {
      cauth.uri.toString mustEqual "http://admin:xyzzy@db.example.com:5984"
    }

    "return the right unauthentified URI in secure mode" in {
      scunauth.uri.toString mustEqual "https://db.example.com:5984"
    }

    "return the right authentified URI in secure mode" in {
      scauth.uri.toString mustEqual "https://admin:xyzzy@db.example.com:5984"
    }

    "complete missing arguments" in {
      (new Couch().uri.toString mustEqual "http://localhost:5984") &&
        (new Couch(auth = Some("admin", "xyzzy")).uri.toString mustEqual "http://admin:xyzzy@localhost:5984") &&
        (new Couch("db.example.com").uri.toString mustEqual "http://db.example.com:5984") &&
        (new Couch("db.example.com", auth = Some("admin", "xyzzy")).uri.toString mustEqual "http://admin:xyzzy@db.example.com:5984") &&
        (new Couch("db.example.com", 80, Some("admin", "xyzzy")).uri.toString mustEqual "http://admin:xyzzy@db.example.com")
    }

    "mask the password in toString" in {
      (new Couch().toString mustEqual "http://localhost:5984") &&
        (new Couch(auth = Some("admin", "xyzzy")).toString mustEqual "http://admin:********@localhost:5984") &&
        (new Couch(auth = Some("admin", "xyzzy"), secure = true).toString mustEqual "https://admin:********@localhost:5984") &&
        (new Couch("db.example.com", 80, Some("admin", "xyzzy")).toString mustEqual "http://admin:********@db.example.com") &&
        (new Couch("db.example.com", 443, Some("admin", "xyzzy"), true).toString mustEqual "https://admin:********@db.example.com")
    }

    "properly analyze the status" in {
      val status = Json.parse("""{"couchdb":"Welcome","version":"1.3.0a-0c6f529-git","vendor":{"version":"1.3.0a-0c6f529-git","name":"The Apache Software Foundation"}}""").as[Couch.Status]
      (status.couchdb mustEqual "Welcome") &&
        (status.version mustEqual "1.3.0a-0c6f529-git") &&
        (status.vendor.get.version.get mustEqual "1.3.0a-0c6f529-git") &&
        (status.vendor.get.name mustEqual "The Apache Software Foundation")
    }
  }

  val dbunauth = Database(cunauth, "test")
  val dbauth = Database(cauth, "test")

  "The 'Database' class" should {
    "return the right unauthentified URI" in {
      dbunauth.uri.toString mustEqual "http://db.example.com:5984/test"
    }

    "return the right authentified URI" in {
      dbauth.uri.toString mustEqual "http://admin:xyzzy@db.example.com:5984/test"
    }

    "return the URI as toString" in {
      dbauth.toString mustEqual "http://admin:xyzzy@db.example.com:5984/test"
    }

    "return the right URI in local context" in {
      dbauth.uriFrom(cauth) mustEqual "test"
    }

    "return the right URI in remote context" in {
      dbauth.uriFrom(cunauth) mustEqual "http://admin:xyzzy@db.example.com:5984/test"
    }

    "properly analyze the status" in {
      val status = Json.parse("""{"db_name":"episodes","doc_count":110,"doc_del_count":656,"update_seq":780,"purge_seq":0,"compact_running":false,"disk_size":532600,"data_size":228323,"instance_start_time":"1323036107518987","disk_format_version":6,"committed_update_seq":780}""").as[Map[String, JsValue]]
      (status("db_name").as[String] mustEqual "episodes") &&
        (status("doc_count").as[Int] mustEqual 110) &&
        (status("doc_del_count").as[Int] mustEqual 656) &&
        (status("update_seq").as[Int] mustEqual 780) &&
        (status("purge_seq").as[Int] mustEqual 0) &&
        (status("compact_running").as[Boolean] mustEqual false) &&
        (status("disk_size").as[Long] mustEqual 532600L) &&
        (status("data_size").as[Long] mustEqual 228323L) &&
        (status("instance_start_time").as[String].toLong mustEqual 1323036107518987L) &&
        (status("disk_format_version").as[Int] mustEqual 6) &&
        (status("committed_update_seq").as[Int] mustEqual 780)
    }

  }

}
