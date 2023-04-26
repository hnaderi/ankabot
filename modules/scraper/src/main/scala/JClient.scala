package io.aibees.knowledgebase

import cats.effect.IO
import java.net.http.{HttpClient => JHttpClient}
import scala.concurrent.duration.*

object JClient {
  def apply(timeout: FiniteDuration = 5.seconds): IO[JHttpClient] = IO(
    JHttpClient
      .newBuilder()
      .connectTimeout(java.time.Duration.ofSeconds(timeout.toSeconds))
      .build
  )
}
