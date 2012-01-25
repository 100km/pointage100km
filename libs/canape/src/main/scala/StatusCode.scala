package net.rfc1149.canape

case class StatusCode(code: Int, reason: String) extends RuntimeException(code + " " + reason)
