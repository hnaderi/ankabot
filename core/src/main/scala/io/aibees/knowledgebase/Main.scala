package io.aibees.knowledgebase

import cats.effect.IO
import cats.effect.IOApp
import io.odin.Logger

object Main extends IOApp.Simple {
  private given Logger[IO] = io.odin.consoleLogger[IO]()

  def run: IO[Unit] =
    Application().compile.drain
}
