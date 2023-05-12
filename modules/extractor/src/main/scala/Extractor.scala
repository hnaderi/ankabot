package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import fs2.Stream.*
import fs2.io.file.Path
import io.circe.syntax.*
import io.odin.Logger

import java.net.URI
import scala.util.matching.Regex

object Extractor {

  def apply(input: Stream[IO, WebsiteData], output: Path, maxParallel: Int)(
      using logger: Logger[IO]
  ): Stream[IO, Unit] = for {
    patterns <- eval(Technology.load)
    extractor = extractors.All(patterns)
    metrics <- ExtractionMetricsCollector.printer()
    _ <- input
      .map { fetched =>
        for {
          home <- evals(getPage(fetched.home))
          children <- eval(fetched.children.traverse(getPage).map(_.flatten))
          homeX <- eval(extractor.io(home))
          allX <- eval(children.traverse(extractor.io)).map(
            _.combineAll.combine(homeX)
          )
          _ <- eval(
            metrics
              .add(
                contactsHome = homeX.contacts,
                contactsAll = allX.contacts,
                technologiesHome = homeX.technologies,
                technologiesAll = allX.technologies
              )
          )

        } yield ExperimentData(
          source = fetched.home.source,
          contacts = allX.contacts,
          technologies = allX.technologies,
          children = home.page.childPages
        )
      }
      .parJoin(maxParallel)
      .through(Storage.persist(output))
  } yield ()

  private def getPage(fetch: FetchResult): IO[Option[ToExtract]] =
    fetch.result match {
      case Left(_) => IO(None)
      case Right(result) =>
        IO.interruptibleMany(JsoupWebPage(result))
          .map(_.toOption.map(ToExtract(_, result)))
    }
}

final case class ToExtract(
    page: WebPage,
    data: FetchedData
)
