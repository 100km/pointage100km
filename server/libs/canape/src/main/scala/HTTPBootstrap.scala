package net.rfc1149.canape

import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel.ChannelFuture

trait HTTPBootstrap {

  protected[this] val bootstrap: ClientBootstrap

  def connect(): ChannelFuture

  def releaseExternalResources()

}
