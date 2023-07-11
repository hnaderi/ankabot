package dev.hnaderi.ankabot

import cats.effect.IO
import cats.syntax.all.*
import dev.hnaderi.ankabot.extractors.ExtractorConfig
import fs2.Stream
import fs2.Stream.*
import io.odin.Logger

import scala.concurrent.duration.Duration

object ExtractorRaw {
  def apply(
      input: Stream[IO, RawData],
      maxParallel: Int,
      config: ExtractorConfig = ExtractorConfig.default
  )(using
      logger: Logger[IO]
  ): Stream[IO, WebsiteExtractedData] = {
    def getPage(data: FetchedData) =
      IO.interruptibleMany(JsoupWebPage(data))
        .map(_.toOption.map(ToExtract(_, data)))
    val emptyData = IO(ExtractedData())

    for {
      extractor <- eval(extractors.Builder(config))
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
