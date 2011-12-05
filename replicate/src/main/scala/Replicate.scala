import dispatch._

object Replicate {

  def replicateReq(command: Database, source: Database, target: Database) = {
    val params = """{"source": """" + source.databaseURI(command == source) +
                 """", "target": """" + target.databaseURI(command == target) +
		 """","continuous":true}"""
    command.replicateReq << (params, "application/json")
  }

  def startReplication(local: Database, remote: Database) = {
    val h = new Http
    h(replicateReq(local, local, remote) >|)
    h(replicateReq(local, remote, local) >|)
    h.shutdown()
  }

  def main(args: Array[String]) = {
    val local = Database("localhost", 5984, "steenwerck100km", Some(("admin", "admin")))
    val remote = Database("couchdb.mindslicer.com", 80, "steenwerck100km", Some(("admin", "p4p4n03l")))
    startReplication(local, remote)

  }

}
