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

  def apply(input: Stream[IO, FetchResult], output: Path, maxParallel: Int)(
      using logger: Logger[IO]
  ): Stream[IO, Unit] =
    input
      .parEvalMapUnordered(maxParallel) { fetched =>
        fetched.result match {
          case Left(err) =>
            logger.info(s"Skipping ${fetched.source} due to $err").as(None)
          case Right(result) =>
            IO(JsoupWebPage(result)).flatMap {
              case Left(err) =>
                logger
                  .error(s"Couldn't parse page for ${fetched.source}", err)
                  .as(None)
              case Right(page) =>
                ExperimentData(
                  source = fetched.source,
                  contacts = Extractors.all(page).contacts,
                  children = page.childPages
                ).some.pure
            }
        }
      }
      .unNone
      .through(Storage.persist(output))

  private def interesting(link: Link): Boolean = {
    val text = link.text.toLowerCase
    val url = link.value.toString

    text.contains("contact") || text.contains("about")
  }
}
