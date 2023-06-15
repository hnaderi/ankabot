package dev.hnaderi.ankabot

import cats.effect.IO
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.monovore.decline.Argument
import fs2.Pipe
import fs2.Stream
import fs2.Stream.*
import fs2.io.file.Path
import io.circe.syntax.*
import io.odin.Logger

import java.net.URI
import scala.concurrent.duration.*

type Scraper = URI => IO[WebsiteData]
object Scraper {
  final case class Config(
      timeout: FiniteDuration = 5.seconds,
      maxConcurrentPage: Short = 10,
      maxConcurrentFetch: Short = 30,
      maxChildren: Short = 0,
      maxRedirect: Short = 5,
      backend: ScrapeBackend = ScrapeBackend.JDK
  )

  def apply(
      sources: Stream[IO, URI],
      config: Config
  )(using logger: Logger[IO]): Stream[IO, WebsiteData] =
    build(config).flatMap(sources.parEvalMapUnordered(config.maxConcurrentPage))

  def build(config: Config)(using logger: Logger[IO]): Stream[IO, Scraper] =
    for {
      metrics <- ScrapeMetrics.printer()
      fetcher <- resource(config.backend match {
        case ScrapeBackend.JDK   => JClient(config.timeout)
        case ScrapeBackend.Ember => EClient(config.timeout)
      }).evalMap(
        Fetcher(
          _,
          timeout = config.timeout,
          maxConcurrent = config.maxConcurrentFetch,
          maxRedirects = config.maxRedirect
        )
      )
    } yield run(config.maxChildren, fetcher, metrics)

  private def run(maxChildren: Int, fetcher: Fetcher, metrics: ScrapeMetrics)(
      using logger: Logger[IO]
  ): Scraper = target =>
    fetcher(target).flatMap { home =>
      home.result match {
        case Right(result) if maxChildren > 0 =>
          IO(JsoupWebPage(result)).flatMap {
            case Left(err) =>
              val data = WebsiteData(time = home.time, home = home)
              logger
                .error(
                  s"Couldn't parse fetched data for ${result.url}!",
                  err
                ) >>
                metrics.add(data).as(data)
            case Right(homePage) =>
              val children = homePage.childPages.toList
              for {
                _ <- logger.info(
                  s"Found ${children.size} child pages in ${result.url}"
                )
                dive <- children
                  .take(maxChildren)
                  .parUnorderedTraverse(fetcher)
                  .timed
                (childTime, ch) = dive
                data = WebsiteData(
                  time = home.time + childTime,
                  home = home,
                  children = ch
                )
                _ <- metrics.add(data, Some(children.size))
              } yield data
          }
        case _ =>
          val data = WebsiteData(time = home.time, home = home)
          metrics.add(data).as(data)
      }
    }
}

enum ScrapeBackend {
  case Ember, JDK
}
