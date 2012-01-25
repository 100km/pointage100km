package net.rfc1149.canape

import org.jboss.netty.handler.codec.http._

trait CouchRequest[T] {

  def execute(): T

}

class SimpleCouchRequest[T: Manifest](couch: Couch, val request: HttpRequest) extends CouchRequest[T] {

  override def execute(): T = couch.execute(request)

}

class TransformerRequest[T, U](request: CouchRequest[T], transformer: T => U) extends CouchRequest[U] {

  override def execute(): U = transformer(request.execute)

}
