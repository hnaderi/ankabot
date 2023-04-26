package io.aibees.knowledgebase

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import fs2.io.file.Path
import io.odin.Logger

object Scraper extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    if args.size == 2 then
      Application(Path(args(0)), Path(args(1))).compile.drain
        .as(ExitCode.Success)
    else IO.println("Must provide source and output files!").as(ExitCode.Error)

  private given Logger[IO] = io.odin.consoleLogger[IO]()
}
