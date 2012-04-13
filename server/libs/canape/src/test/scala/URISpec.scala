package net.rfc1149.canape

import akka.actor.ActorSystem
import net.liftweb.json._
import org.specs2.mutable._

class URISpec extends Specification {

  import implicits._

  implicit val dispatcher = ActorSystem().dispatcher

  val cunauth = new NioCouch("db.example.com", 5984)
  val cauth = new NioCouch("db.example.com", 5984, Some(("admin", "xyzzy")))

  "The 'Couch' class" should {
    "return the right unauthentified URI" in {
      cunauth.uri mustEqual "http://db.example.com:5984"
    }

    "return the right authentified URI" in {
      cauth.uri mustEqual "http://admin:xyzzy@db.example.com:5984"
    }

    "complete missing arguments" in {
      ((new NioCouch()).uri mustEqual "http://localhost:5984") &&
      ((new NioCouch(auth = Some("admin", "xyzzy"))).uri mustEqual "http://admin:xyzzy@localhost:5984") &&
      ((new NioCouch("db.example.com")).uri mustEqual "http://db.example.com:5984") &&
      ((new NioCouch("db.example.com", auth = Some("admin", "xyzzy"))).uri mustEqual "http://admin:xyzzy@db.example.com:5984") &&
      ((new NioCouch("db.example.com", 80, Some("admin", "xyzzy"))).uri mustEqual "http://admin:xyzzy@db.example.com:80")
    }

    "properly analyze the status" in {
      val status = parse("""{"couchdb":"Welcome","version":"1.3.0a-0c6f529-git","vendor":{"version":"1.3.0a-0c6f529-git","name":"The Apache Software Foundation"}}""").extract[Couch.Status]
      (status.couchdb mustEqual "Welcome") &&
      (status.version mustEqual "1.3.0a-0c6f529-git") &&
      (status.vendor.get.version mustEqual "1.3.0a-0c6f529-git") &&
      (status.vendor.get.name mustEqual "The Apache Software Foundation")
    }
  }

  val dbunauth = Database(cunauth, "test")
  val dbauth = Database(cauth, "test")

  "The 'Database' class" should {
    "return the right unauthentified URI" in {
      dbunauth.uri mustEqual "http://db.example.com:5984/test"
    }

    "return the right authentified URI" in {
      dbauth.uri mustEqual "http://admin:xyzzy@db.example.com:5984/test"
    }

    "return the right URI in local context" in {
      dbauth.uriFrom(cauth) mustEqual "test"
    }

    "return the right URI in remote context" in {
      dbauth.uriFrom(cunauth) mustEqual "http://admin:xyzzy@db.example.com:5984/test"
    }

    "properly analyze the status" in {
      val status = parse("""{"db_name":"episodes","doc_count":110,"doc_del_count":656,"update_seq":780,"purge_seq":0,"compact_running":false,"disk_size":532600,"data_size":228323,"instance_start_time":"1323036107518987","disk_format_version":6,"committed_update_seq":780}""").extract[Map[String, JValue]]
      (status("db_name").extract[String] mustEqual "episodes") &&
      (status("doc_count").extract[Int] mustEqual 110) &&
      (status("doc_del_count").extract[Int] mustEqual 656) &&
      (status("update_seq").extract[Int] mustEqual 780) &&
      (status("purge_seq").extract[Int] mustEqual 0) &&
      (status("compact_running").extract[Boolean] mustEqual false) &&
      (status("disk_size").extract[Long] mustEqual 532600L) &&
      (status("data_size").extract[Long] mustEqual 228323L) &&
      (status("instance_start_time").extract[String].toLong mustEqual 1323036107518987L) &&
      (status("disk_format_version").extract[Int] mustEqual 6) &&
      (status("committed_update_seq").extract[Int] mustEqual 780)
    }

  }

}
