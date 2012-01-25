package net.rfc1149.canape

import implicits._

import akka.actor.{Actor, ActorRef}

import java.net.{InetSocketAddress, URI}
import java.util.concurrent.Executors

import net.liftweb.json._

import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.util.CharsetUtil

object akkaNetty extends App {

  val couch = new NioCouch("localhost", 5984, Some("admin", "admin"))
  val db = new Database(couch, "steenwerck100km")

  try {
    println("Asking for status")
    val status = db.status.execute
    println("Got: " + status)
  } catch {
      case t: Throwable =>
	println("Caught throwable: " + t)
  }

  try {
    println("Getting document")
    val status = db("checkpoints-0-1").execute
    println("Got: " + status)
  } catch {
      case t: Throwable =>
	println("Caught throwable: " + t)
  }

/*
  val future : ChannelFuture = couch.connect(true)

  future.onConnected { future =>
    if (future.isSuccess) {
      println("Connected")
      // val request = couch.makeGetRequest("steenwerck100km/checkpoints-0-1")
      val request = db.changes(Map("feed" -> "continuous", "timeout" -> "5000"))
      request.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
      val channel = future.getChannel
      channel.onException { (ctx, e) =>	println("channel exception: " + e) }
      channel.onClose { _ => println("channel has been closed") }
      channel.write(request)
      channel.addLast("handler") { js: JObject => println("Received event: " + js) }
    } else {
      println("Not connected, closing: " + future.getCause)
    }
  }

  println("Waiting for connection completion")
  future.getChannel.getCloseFuture().awaitUninterruptibly()
*/
  couch.releaseExternalResources
}

class HttpClientHandler extends SimpleChannelUpstreamHandler {

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    println("Received event " + e)
  }

}
