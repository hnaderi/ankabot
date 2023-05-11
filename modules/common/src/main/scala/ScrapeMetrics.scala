package io.aibees.knowledgebase

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all.*
import io.odin.Logger

import scala.concurrent.duration.*

final class ScrapeMetrics private (stats: Ref[IO, Statistics]) extends AnyVal {
  def add(result: WebsiteData, actualChildCount: Option[Int] = None): IO[Unit] =
    stats.update(_.add(result, actualChildCount))
  def add(result: FetchResult, childCount: Long): IO[Unit] =
    stats.update(_.add(result, childCount))

  def print: IO[String] = stats.get.map(_.show)
  def read: IO[Statistics] = stats.get
}

object ScrapeMetrics {
  def apply(stats: Statistics = Statistics()): IO[ScrapeMetrics] =
    IO.ref(stats).map(new ScrapeMetrics(_))
  def printer(
      stats: Statistics = Statistics(),
      interval: FiniteDuration = 10.seconds
  )(using logger: Logger[IO]): fs2.Stream[IO, ScrapeMetrics] =
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
