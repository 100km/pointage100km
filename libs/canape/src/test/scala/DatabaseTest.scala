package net.rfc1149.canape

import net.liftweb.json.parse
import org.specs2.mutable._

class DatabaseTest extends Specification {

  val cunauth = Couch("db.example.com", 5984)
  val cauth = Couch("db.example.com", 5984, Some(("admin", "xyzzy")))

  "The 'Couch' class" should {
    "return the right unauthentified URI" in {
      cunauth.uri mustEqual "http://db.example.com:5984"
    }

    "return the right authentified URI" in {
      cauth.uri mustEqual "http://admin:xyzzy@db.example.com:5984"
    }

    "complete missing arguments" in {
      (Couch().uri mustEqual "http://localhost:5984") &&
      (Couch("admin", "xyzzy").uri mustEqual "http://admin:xyzzy@localhost:5984") &&
      (Couch("db.example.com").uri mustEqual "http://db.example.com:5984") &&
      (Couch("db.example.com", "admin", "xyzzy").uri mustEqual "http://admin:xyzzy@db.example.com:5984") &&
      (Couch("db.example.com", 80, "admin", "xyzzy").uri mustEqual "http://admin:xyzzy@db.example.com:80")
    }

    "properly analyze the status" in {
      val status = new CouchStatus(parse("""{"couchdb":"Welcome","version":"1.3.0a-0c6f529-git","vendor":{"version":"1.3.0a-0c6f529-git","name":"The Apache Software Foundation"}}"""))
      (status.couchdb mustEqual "Welcome") &&
      (status.version mustEqual "1.3.0a-0c6f529-git") &&
      (status.vendorVersion mustEqual Some("1.3.0a-0c6f529-git")) &&
      (status.vendorName mustEqual Some("The Apache Software Foundation"))
    }
  }

  val dbunauth = Db(cunauth, "test")
  val dbauth = Db(cauth, "test")

  "The 'Db' class" should {
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
      val status = new DbStatus(parse("""{"db_name":"episodes","doc_count":110,"doc_del_count":656,"update_seq":780,"purge_seq":0,"compact_running":false,"disk_size":532600,"data_size":228323,"instance_start_time":"1323036107518987","disk_format_version":6,"committed_update_seq":780}"""))
      (status.db_name mustEqual "episodes") &&
      (status.doc_count mustEqual 110) &&
      (status.doc_del_count mustEqual 656) &&
      (status.update_seq mustEqual 780) &&
      (status.purge_seq mustEqual 0) &&
      (status.compact_running mustEqual false) &&
      (status.disk_size mustEqual 532600L) &&
      (status.data_size mustEqual Some(228323L)) &&
      (status.instance_start_time mustEqual 1323036107518987L) &&
      (status.disk_format_version mustEqual 6) &&
      (status.committed_update_seq mustEqual 780)
    }

  }

}
