package net.rfc1149.canape

import org.jboss.netty.bootstrap.ClientBootstrap

trait HTTPBootstrap {

  protected[this] val bootstrap: ClientBootstrap

  def releaseExternalResources() = bootstrap.releaseExternalResources()

}
