package io.aibees.knowledgebase

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import com.monovore.decline.Command

abstract class CMDApp[T](cmd: Command[T]) extends IOApp {
  override final def run(args: List[String]): IO[ExitCode] =
    cmd.parse(args) match {
      case Left(value)  => IO.println(value).as(ExitCode.Success)
      case Right(value) => app(value).compile.drain.as(ExitCode.Success)
    }

  def app(t: T): fs2.Stream[IO, Unit]
}
