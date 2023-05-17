package io.aibees.knowledgebase

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.effect.syntax.all.*
import io.odin.Logger
import org.http4s.Header
import org.http4s.MediaType
import org.http4s.Method.GET
import org.http4s.Request
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.dsl.io.*
import org.http4s.client.middleware.FollowRedirect
import org.http4s.client.middleware.GZip

import java.net.URI
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*

type Fetcher = URI => IO[FetchResult]

object Fetcher {
  private def create(
      client: Client[IO],
      timeoutDuration: FiniteDuration
  )(using logger: Logger[IO]): Fetcher =
    juri =>
      buildUri(juri)
        .flatMap { uri =>
          val req = GET.apply(
            uri,
            Header(
              "User-Agent",
              "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/112.0"
            ),
            Header(
              "Accept",
              "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
            ),
            Header("Accept-Language", "en-US,en;q=0.5"),
            Header("DNT", "1"),
            Header("Upgrade-Insecure-Requests", "1"),
            Header("Connection", "keep-alive"),
            Header("Sec-Fetch-Dest", "document"),
            Header("Sec-Fetch-Mode", "navigate"),
            Header("Sec-Fetch-Site", "none"),
            Header("Sec-Fetch-User", "?1")
          )

          logger.debug(s"request: ${req.asCurl(_ => false)}") >>
            client
              .run(req)
              .use { resp =>
                val redirected = FollowRedirect
                  .getRedirectUris(resp)
                val baseURI = redirected.lastOption
                  .map(_.toString)
                  .map(URI.create)
                  .getOrElse(juri)

                logger.debug(s"redirected $redirected") >>
                  resp.bodyText.compile.string.timed.map((time, body) =>
                    FetchedData(
                      url = baseURI,
                      resp.headers.headers.map(h => (h.name.toString, h.value)),
                      resp.status.code,
                      HttpVersion(
                        resp.httpVersion.major,
                        resp.httpVersion.minor
                      ),
                      resp.cookies
                        .map(rc =>
                          Cookie(
                            name = rc.name,
                            content = rc.content,
                            domain = rc.domain,
                            path = rc.path
                          )
                        )
                        .toSet,
                      body
                    )
                  )
              }
        }
        .timeout(timeoutDuration)
        .map(Right(_))
        .recover {
          case _: TimeoutException => Left(FetchError.Timeout)
          case _                   => Left(FetchError.Failed)
        }
        .timed
        .map((time, r) => FetchResult(juri, r, time))

  def simple(
      client: Client[IO],
      timeout: FiniteDuration = 5.seconds,
      maxConcurrent: Int = 30
  )(using logger: Logger[IO]): IO[Fetcher] =
    Semaphore[IO](maxConcurrent).flatMap { sem =>
      val fetch = create(client, timeout)
      client
        .expect[String]("https://api.my-ip.io/ip")
        .attempt
        .flatMap {
          case Right(ip) => logger.info(s"Fetching from IP: $ip")
          case Left(err) => logger.error("Cannot determine external IP!", err)
        }
        .as(uri => sem.permit.use(_ => fetch(uri)))
    }

  def apply(
      client: Client[IO],
      timeout: FiniteDuration = 5.seconds,
      maxRedirects: Int = 5
  )(using Logger[IO]): IO[Fetcher] = simple(
    FollowRedirect(maxRedirects)(GZip()(client)),
    timeout
  )

  private def buildUri(uri: URI): IO[Uri] =
    IO.fromEither(Uri.fromString(uri.toString))

}
