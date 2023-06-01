package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import fs2.Stream.*
import fs2.io.file.Path
import io.circe.syntax.*
import io.odin.Logger

import java.net.URI

type Extractor = ToExtract => IO[ExtractedData]
object Extractor {
  def build(patterns: IO[TechnologyMap] = Technology.load): IO[Extractor] =
    patterns.map(extractors.All(_).io)

  def apply(
      input: Stream[IO, WebsiteData],
      output: Path,
      maxParallel: Int,
      extractChild: Boolean = true
  )(using
      logger: Logger[IO]
  ): Stream[IO, Unit] = for {
    extractor <- eval(build())
    metrics <- ExtractionMetricsCollector.printer()
    reporter <- StatusReporter[URI]()
    _ <- input
      .map { fetched =>
        for {
          _ <- resource(reporter.report(fetched.home.source))
          home <- evals(getPage(fetched.home))
          children <- eval(
            (if extractChild then fetched.children else Nil)
              .traverse(getPage)
              .map(_.flatten)
          )
          (homeTime, homeX) <- eval(extractor(home).timed)
          (allTime, allX) <- eval(children.traverse(extractor).timed).map(
            (t, ch) => (t + homeTime, ch.combineAll.combine(homeX))
          )
          _ <- eval(
            metrics
              .add(
                contactsHome = homeX.contacts,
                contactsAll = allX.contacts,
                technologiesHome = homeX.technologies,
                technologiesAll = allX.technologies,
                timeHome = homeTime,
                timeAll = allTime
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

  def getPage(fetch: FetchResult): IO[Option[ToExtract]] =
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
