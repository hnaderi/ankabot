package dev.hnaderi.ankabot

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import fs2.Stream
import fs2.Stream.*
import io.odin.Logger

import scala.concurrent.duration.*

final class StatusReporter[K](
    data: Ref[IO, Map[K, FiniteDuration]]
) {
  private def add(key: K): IO[Unit] =
    IO.realTime.flatMap(d => data.update(_.updated(key, d)))
  private def remove(key: K): IO[Unit] = data.update(_ - key)

  def report(key: K): Resource[IO, Unit] =
    Resource.make(add(key))(_ => remove(key))
}

object StatusReporter {
  def apply[K](
      interval: FiniteDuration = 5.seconds
  )(using logger: Logger[IO]): Stream[IO, StatusReporter[K]] = for {
    data <- eval(IO.ref(Map.empty[K, FiniteDuration]))
    _ <- awakeEvery[IO](interval)
      .evalMap(_ => data.get.product(IO.realTime))
      .foreach((data, now) =>
        val view =
          data.map((k, v) => s"$k -> ${(now - v).toCoarsest}").mkString("\n")
        logger.info(view)
      )
      .spawn
  } yield new StatusReporter(data)
}
