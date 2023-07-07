package dev.hnaderi.ankabot

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import fs2.Stream.*
import io.odin.Logger

import java.net.URI

type Extractor = ToExtract => IO[ExtractedData]
object Extractor {
  def build(patterns: IO[TechnologyMap] = Technology.load): IO[Extractor] =
    patterns.map(extractors.All(_).io)

  def apply(
      input: Stream[IO, WebsiteData],
      maxParallel: Int,
      extractChild: Boolean = true
  )(using
      logger: Logger[IO]
  ): Stream[IO, WebsiteExtractedData] = (for {
    extractor <- eval(build())
    metrics <- ExtractionMetricsCollector.printer()
    reporter <- StatusReporter[URI]()
    jobs <- input
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
          _ <- eval(metrics.add(allX, allTime))

        } yield WebsiteExtractedData(
          domain = fetched.home.source,
          extracted = allX,
          pages = home.page.childPages
        )
      }
  } yield jobs)
    .parJoin(maxParallel)

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
