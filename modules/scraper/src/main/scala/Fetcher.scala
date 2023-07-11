package dev.hnaderi.ankabot

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Semaphore
import io.odin.Logger
import org.http4s.Header
import org.http4s.Method.GET
import org.http4s.Response
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.dsl.io.*
import org.http4s.client.middleware.FollowRedirect
import org.http4s.client.middleware.GZip
import org.http4s.headers.`Content-Length`
import org.http4s.headers.`Content-Type`

import java.net.URI
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*

type Fetcher = URI => IO[FetchResult]

object Fetcher {
  private val headers: Header.ToRaw = Seq(
    "User-Agent" -> "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/112.0",
    "Accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language" -> "en-US,en;q=0.5",
    "DNT" -> "1",
    "Upgrade-Insecure-Requests" -> "1",
    "Connection" -> "keep-alive",
    "Sec-Fetch-Dest" -> "document",
    "Sec-Fetch-Mode" -> "navigate",
    "Sec-Fetch-Site" -> "none",
    "Sec-Fetch-User" -> "?1"
  )
  private final val maxAcceptableSize = 1024 * 1024 // 1 MB

  private def isOkToContinue(resp: Response[IO]): Boolean = {
    val contentType = resp.headers.get[`Content-Type`]
    val contentLength = resp.headers.get[`Content-Length`]
    def okType(value: `Content-Type`) = value.mediaType.isText
    def okLength(value: `Content-Length`) = value.length <= maxAcceptableSize

    (contentLength, contentType) match {
      case (Some(length), Some(ctype)) if okType(ctype) && okLength(length) =>
        true
      case (None, Some(ctype)) if okType(ctype)     => true
      case (Some(length), None) if okLength(length) => true
      case _                                        => false
    }
  }

  extension (req: Resource[IO, Response[IO]]) {
    private def guard(
        handle: Response[IO] => IO[FetchedData]
    ): IO[Either[FetchError, FetchedData]] = req.use(res =>
      if isOkToContinue(res) then handle(res).map(Right(_))
      else IO(Left(FetchError.BadContent))
    )
  }

  private def create(
      client: Client[IO],
      timeoutDuration: FiniteDuration
  )(using Logger[IO]): Fetcher =
    juri =>
      buildUri(juri)
        .flatMap { uri =>
          val req = GET(uri, headers)

          client
            .run(req)
            .guard { resp =>
              val redirected = FollowRedirect
                .getRedirectUris(resp)
              val baseURI = redirected.lastOption
                .map(_.toString)
                .map(URI.create)
                .getOrElse(juri)

              resp.bodyText.compile.string.map(body =>
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
      maxConcurrent: Int = 30,
      maxRedirects: Int = 5
  )(using Logger[IO]): IO[Fetcher] = simple(
    FollowRedirect(maxRedirects, _ => false)(GZip()(client)),
    timeout = timeout,
    maxConcurrent = maxConcurrent
  )

  private def buildUri(uri: URI): IO[Uri] =
    IO.fromEither(Uri.fromString(uri.toString))

}
