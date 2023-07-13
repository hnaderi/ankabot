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
    val emptyData = IO((ExtractedData.empty, WebsiteInfoData.empty))

    for {
      extractor <- eval(extractors.Builder(config))
      metrics <- ExtractionMetricsCollector.printer()
      jobs <- input.parEvalMap(maxParallel) { fetched =>
        fetched.pages
          .traverse(currentPage =>
            getPage(currentPage).flatMap {
              case Some(page) =>
                val info = extractors.InfoExtractor(page.page)
                extractor(page).map((_, info))
              case None => emptyData
            }.timed
          )
          .flatMap { x =>
            val data = x.foldMap(_._2._1)
            val spent = x.foldLeft(Duration.Zero)(_ + _._1)
            val info =
              x.headOption.map(_._2._2).getOrElse(WebsiteInfoData.empty)

            metrics
              .add(data, spent)
              .as(
                WebsiteExtractedData(
                  fetched.domain,
                  data,
                  fetched.pages.map(_.url).toSet,
                  info = info
                )
              )
          }
      }
    } yield jobs
  }
}
