package net.rfc1149.canape

import akka.dispatch.ExecutionContext

class NioCouch(host: String = "localhost",
               port: Int = 5984,
               auth: Option[(String, String)] = None)(override implicit val context: ExecutionContext)
  extends Couch(host, port, auth) with NioHTTPBootstrap

