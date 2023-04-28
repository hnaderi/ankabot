package io.aibees.knowledgebase

import com.monovore.decline.Argument
import fs2.io.file.Path
import java.nio.file.{Path as JPath}
import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.IO
import com.monovore.decline.Command

given Argument[Path] = Argument[JPath].map(Path.fromNioPath)

abstract class CMDApp[T](cmd: Command[T]) extends IOApp {
  override final def run(args: List[String]): IO[ExitCode] =
    cmd.parse(args) match {
      case Left(value)  => IO.println(value).as(ExitCode.Success)
      case Right(value) => app(value).compile.drain.as(ExitCode.Success)
    }

  def app(t: T): fs2.Stream[IO, Unit]
}
