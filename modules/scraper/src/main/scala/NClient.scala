package io.aibees.knowledgebase

import cats.effect.IO
import cats.effect.kernel.Resource
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.netty.client.NettyClientBuilder

import scala.concurrent.duration.*

object NClient {
  def apply(timeout: FiniteDuration = 5.seconds): Resource[IO, Client[IO]] =
    NettyClientBuilder[IO].withProxyFromSystemProperties
      .withIdleTimeout(timeout)
      .resource
}

object EClient {
  def apply(timeout: FiniteDuration = 5.seconds): Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].withHttp2.withTimeout(timeout).build
}
