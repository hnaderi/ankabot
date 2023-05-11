package io.aibees.knowledgebase

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

def Scraper(
    sources: Stream[IO, URI],
    timeout: FiniteDuration,
    maxConcurrentPage: Int,
    maxConcurrentFetch: Int,
    maxChildren: Int,
    backend: ScrapeBackend,
    result: Path
)(using logger: Logger[IO]): Stream[IO, Unit] = for {
  metrics <- Metrics.printer()
  fetcher <- resource(backend match {
    case ScrapeBackend.Ember => EClient(timeout)
    case ScrapeBackend.Netty => NClient(timeout)
  }).evalMap(Fetcher(_, timeout, maxConcurrentFetch))

  _ <- sources
    .parEvalMapUnordered(maxConcurrentPage)(src =>
      fetcher(src).flatMap { home =>
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
    )
    .through(Storage.persist(result))
} yield ()

enum ScrapeBackend {
  case Ember, Netty
}

object ScrapeBackend {
  given Argument[ScrapeBackend] = Argument.fromMap(
    "scrape backend",
    ScrapeBackend.values.map(b => b.toString.toLowerCase -> b).toMap
  )
}
