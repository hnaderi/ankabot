package io.aibees.knowledgebase

import cats.effect.IO
import org.http4s.client.Client
import scala.concurrent.duration.*
import org.http4s.netty.client.NettyClientBuilder
import cats.effect.kernel.Resource
import org.http4s.ember.client.EmberClientBuilder

object NClient {
  def apply(timeout: FiniteDuration = 5.seconds): Resource[IO, Client[IO]] =
    NettyClientBuilder[IO].withProxyFromSystemProperties
      .withIdleTimeout(timeout)
      .resource
}

object EClient {
  def apply(timeout: FiniteDuration = 5.seconds): Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].withTimeout(timeout).build
}
