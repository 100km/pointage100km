package akka.http

import akka.actor._
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Timeout
import java.net.URI
import org.jboss.netty.handler.codec.http._

object Test extends App {

  val system = ActorSystem("Test")

  val client = system.actorOf(Props[HttpClient], "HttpClient")

  implicit val timeout: Timeout = 5 seconds

  // val requestor = system.actorOf(Props(new HttpActor(client)), "Requestor")
  // println("requestor: " + requestor)

/*
  val cf = ask(client, Connect("www.rfc1149.net", 80))(5 seconds).mapTo[HttpConnection]
  val connection = Await.result(cf, 5 seconds)

  val r = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
  r.setHeader(HttpHeaders.Names.HOST, "www.rfc1149.net")
  r.setHeader(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP)
  r.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE)
  println(r)

  val answer = Await.result(connection.send(r)(5 seconds), 5 seconds)
  println("answer: " + answer)
*/

  val answer = Await.result(util.perform(client, new URI("http://www.google.com/"), HttpMethod.GET){_ => }, 5 seconds)
  println("answer: " + answer)

  /* connection.close() */

  system.shutdown()

}
