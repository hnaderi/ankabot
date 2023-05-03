package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
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
    maxParallel: Int,
    result: Path
)(using logger: Logger[IO]): Stream[IO, Unit] = for {
  fetcher <- resource(NClient(timeout)).evalMap(Fetcher(_, timeout))
  _ <- sources
    .parEvalMapUnordered(maxParallel)(src =>
      fetcher(src).flatMap { home =>
        home.result match {
          case Left(err) => WebsiteData(time = home.time, home = home).pure
          case Right(result) =>
            IO(JsoupWebPage(result)).flatMap {
              case Left(err) =>
                logger
                  .error(s"Couldn't parse fetched data for ${result.url}!", err)
                  .as(WebsiteData(time = home.time, home = home))
              case Right(homePage) =>
                val children = homePage.childPages.toVector
                logger.info(
                  s"Found ${children.size} children pages in ${result.url}"
                ) >>
                  children
                    .parUnorderedTraverse(fetcher)
                    .map(ch =>
                      WebsiteData(time = home.time, home = home, children = ch)
                    )
            }
        }
      }
    )
    .through(Statistics.calculateNested())
    .through(Storage.persist(result))
} yield ()

private def interesting(link: Link): Boolean = {
  val text = link.text.toLowerCase
  val url = link.value.toString

  text.contains("contact") || text.contains("about")
}
