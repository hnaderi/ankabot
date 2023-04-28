package io.aibees.knowledgebase

import cats.effect.IO
import io.odin.Logger
import org.http4s.Header
import org.http4s.Method.GET
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.dsl.io.*
import org.http4s.client.middleware.FollowRedirect
import org.http4s.Request

import java.net.URI
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*

type Fetcher = URI => IO[FetchResult]

object Fetcher {
  def simple(
      client: Client[IO],
      timeoutDuration: FiniteDuration = 5.seconds
  ): Fetcher = juri =>
    buildUri(juri).flatMap { uri =>
      val req = GET(
        uri,
        Header(
          "User-Agent",
          "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/111.0"
        )
      )

      client
        .run(req)
        .use { resp =>
          resp.bodyText.compile.string.map(body =>
            FetchedData(
              url = juri,
              resp.headers.headers.map(h => (h.name.toString, h.value)),
              resp.status.code,
              HttpVersion(resp.httpVersion.major, resp.httpVersion.minor),
              resp.cookies.map(rc =>
                Cookie(
                  name = rc.name,
                  content = rc.content,
                  domain = rc.domain,
                  path = rc.path
                )
              ),
              body
            )
          )
        }
        .timeout(timeoutDuration)
        .map(Right(_))
        .recover {
          case _: TimeoutException => Left(FetchError.Timeout)
          case _                   => Left(FetchError.Failed)
        }
        .map(FetchResult(juri, _))
    }

  def apply(
      client: Client[IO],
      timeout: FiniteDuration = 5.seconds,
      maxRedirects: Int = 3
  ): Fetcher = simple(
    FollowRedirect(maxRedirects)(client),
    timeout
  )

  private def buildUri(uri: URI): IO[Uri] =
    IO.fromEither(Uri.fromString(uri.toString))

}
