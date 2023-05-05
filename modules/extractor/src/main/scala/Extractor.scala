package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import fs2.Stream.*
import fs2.io.file.Path
import io.circe.syntax.*
import io.odin.Logger

import java.net.URI

object Extractor {

  def apply(input: Stream[IO, WebsiteData], output: Path, maxParallel: Int)(
      using logger: Logger[IO]
  ): Stream[IO, Unit] = for {
    metrics <- Metrics.printer()
    _ <- input
      .parEvalMapUnordered(maxParallel) { fetched =>
        fetched.home.result match {
          case Left(err) =>
            logger.info(s"Skipping ${fetched.home.source} due to $err").as(None)
          case Right(result) =>
            IO(JsoupWebPage(result)).flatMap {
              case Left(err) =>
                logger
                  .error(s"Couldn't parse page for ${fetched.home.source}", err)
                  .as(None)
              case Right(page) =>
                metrics
                  .add(fetched.home, page.childPages.size)
                  .as(
                    ExperimentData(
                      source = fetched.home.source,
                      contacts = Extractors.all(page).contacts,
                      children = page.childPages
                    ).some
                  )
            }
        }
      }
      .unNone
      .through(Storage.persist(output))
  } yield ()

  private def interesting(link: Link): Boolean = {
    val text = link.text.toLowerCase
    val url = link.value.toString

    text.contains("contact") || text.contains("about")
  }
}
