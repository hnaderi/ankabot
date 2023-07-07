package dev.hnaderi.ankabot

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import fs2.Stream.*
import io.odin.Logger

import scala.concurrent.duration.Duration

object ExtractorRaw {
  def build(patterns: IO[TechnologyMap] = Technology.load): IO[Extractor] =
    patterns.map(extractors.All(_).io)

  def apply(
      input: Stream[IO, RawData],
      maxParallel: Int
  )(using
      logger: Logger[IO]
  ): Stream[IO, WebsiteExtractedData] = {
    def getPage(data: FetchedData) =
      IO.interruptibleMany(JsoupWebPage(data))
        .map(_.toOption.map(ToExtract(_, data)))
    val emptyData = IO(ExtractedData())

    for {
      extractor <- eval(build())
      metrics <- ExtractionMetricsCollector.printer()
      jobs <- input.parEvalMap(maxParallel) { fetched =>
        fetched.pages
          .traverse(currentPage =>
            getPage(currentPage).flatMap {
              case Some(page) => extractor(page)
              case None       => emptyData
            }.timed
          )
          .flatMap { x =>
            val data = x.foldMap(_._2)
            val spent = x.foldLeft(Duration.Zero)(_ + _._1)

            metrics
              .add(data, spent)
              .as(
                WebsiteExtractedData(
                  fetched.domain,
                  data,
                  fetched.pages.map(_.url).toSet
                )
              )
          }
      }
    } yield jobs
  }
}
