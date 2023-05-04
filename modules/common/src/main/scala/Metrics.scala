package io.aibees.knowledgebase

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all.*
import io.odin.Logger

import scala.concurrent.duration.*

final class Metrics private (stats: Ref[IO, Statistics]) extends AnyVal {
  def add(result: FetchResult): IO[Unit] = stats.update(_.add(result))
  def add(result: WebsiteData): IO[Unit] =
    stats.update(d => result.children.foldLeft(d.add(result.home))(_ add _))

  def print: IO[String] = stats.get.map(_.show)
  def read: IO[Statistics] = stats.get
}

object Metrics {
  def apply(): IO[Metrics] = IO.ref(Statistics()).map(new Metrics(_))
  def printer(
      interval: FiniteDuration = 10.seconds
  )(using logger: Logger[IO]): fs2.Stream[IO, Metrics] =
    for {
      stats <- fs2.Stream.eval(apply())
      print = stats.read.flatMap(logger.info)
      _ <- fs2.Stream
        .awakeEvery[IO](interval)
        .foreach(_ => print)
        .spawn
        .onFinalize(logger.info("Terminated") >> print)
    } yield stats
}
