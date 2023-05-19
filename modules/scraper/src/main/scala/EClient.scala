package io.aibees.knowledgebase

import cats.effect.IO
import cats.effect.kernel.Resource
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder

import scala.concurrent.duration.*

object EClient {
  def apply(timeout: FiniteDuration = 5.seconds): Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].withHttp2.withTimeout(timeout).build
}
