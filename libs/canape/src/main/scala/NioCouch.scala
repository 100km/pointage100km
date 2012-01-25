package net.rfc1149.canape

class NioCouch(host: String = "localhost", port: Int = 5984, auth: Option[(String, String)] = None)
  extends Couch(host, port, auth) with NioHTTPBootstrap

