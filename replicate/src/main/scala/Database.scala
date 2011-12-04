import dispatch._

case class Database(val host: String, val port: Int,  val database: String, val credentials: Option[(String, String)] = None) {

  private def ifCredentials[T](ifClause: (String, String) => T)(elseClause: => T) =
    credentials match {
	case Some((login, password)) =>
	  ifClause(login, password)
	case None =>
	  elseClause
    }

  private def addCredentials(req: Request) = ifCredentials(req.as_!(_, _))(req)

  lazy val connectReq = addCredentials(:/(host, port))

  lazy val databaseReq = connectReq / database

  def databaseURI(local: Boolean) =
    if (local)
      database
    else
      ifCredentials((login: String, password: String) => :/(login + ":" + password + "@" + host, port))(:/(host, port)) / database to_uri

  lazy val replicateReq = connectReq / "_replicate"
}
