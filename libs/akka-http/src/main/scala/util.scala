package akka.http

import akka.actor.ActorRef
import akka.dispatch.Future
import akka.pattern.ask
import akka.util.duration._
import akka.util.Timeout
import java.net.URI
import org.jboss.netty.handler.codec.http._

object util {

  def perform(client: ActorRef, uri: URI, method: HttpMethod)(prepare: HttpRequest => Unit)(implicit timeout: Timeout): Future[HttpResponse] = {
    val scheme = uri.getScheme match {
	case null => "http"
	case s    => s
    }
    val port = uri.getPort match {
	case -1 => if (scheme == "http") 80 else 443
	case i  => i
    }
    val request: HttpRequest =
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, method, uri.getPath)
    request.setHeader(HttpHeaders.Names.HOST, uri.getHost)
    request.setHeader(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP)
    request.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
    prepare(request)
    ask(client, Connect(uri.getHost, port)).mapTo[HttpConnection].flatMap(_.send(request))
  }

}
