package io.aibees.knowledgebase

import cats.effect.IO
import org.http4s.Method.GET
import org.http4s.*
import org.http4s.client.Client
import org.http4s.client.dsl.io.*
import scala.concurrent.duration.*
import java.util.concurrent.TimeoutException
import org.http4s.client.middleware.FollowRedirect
import java.net.URI

type FetchResult = ScrapeResult[RawScrapedData]
type Fetcher = Uri => IO[FetchResult]

object Fetcher {
  def simple(
      client: Client[IO],
      timeoutDuration: FiniteDuration = 5.seconds
  ): Fetcher = url => {

    val req = GET(
      url,
      Header(
        "User-Agent",
        "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/111.0"
      )
    )
    client
      .run(req)
      .use { resp =>
        resp.bodyText.compile.string.map(body =>
          RawScrapedData(
            url = URI(url.toString),
            resp.headers.headers.map(h => (h.name.toString, h.value)),
            resp.status.code,
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
      .attempt
      .map {
        case Right(value)              => Right(value)
        case Left(_: TimeoutException) => Left(ScrapeError.Timeout)
        case Left(_)                   => Left(ScrapeError.Failed)
      }
      .map(ScrapeResult(URI(url.toString), _))
  }

  def apply(
      client: Client[IO],
      timeoutDuration: FiniteDuration = 5.seconds,
      maxRedirects: Int = 3
  ): Fetcher = simple(
    FollowRedirect(maxRedirects)(client),
    timeoutDuration
  )
}
