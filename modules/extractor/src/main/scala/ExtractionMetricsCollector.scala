package dev.hnaderi.ankabot

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all.*
import io.odin.Logger

import scala.concurrent.duration.*

final class ExtractionMetricsCollector private (
    stats: Ref[IO, ExtractionMetrics]
) extends AnyVal {
  def add(
      extracted: ExtractedData,
      spentTime: FiniteDuration
  ): IO[Unit] =
    stats.update(_.add(extracted, spentTime))

  def print: IO[String] = stats.get.map(_.show)
  def read: IO[ExtractionMetrics] = stats.get
}

object ExtractionMetricsCollector {
  def apply(
      stats: ExtractionMetrics = ExtractionMetrics()
  ): IO[ExtractionMetricsCollector] =
    IO.ref(stats).map(new ExtractionMetricsCollector(_))

  def printer(
      stats: ExtractionMetrics = ExtractionMetrics(),
      interval: FiniteDuration = 30.seconds
  )(using logger: Logger[IO]): fs2.Stream[IO, ExtractionMetricsCollector] =
    for {
      stats <- fs2.Stream.eval(apply(stats))
      print = stats.read.flatMap(logger.info)
      _ <- fs2.Stream
        .awakeEvery[IO](interval)
        .foreach(_ => print)
        .spawn
        .onFinalize(logger.info("Terminated") >> print)
    } yield stats
}
