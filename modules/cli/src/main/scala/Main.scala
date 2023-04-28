package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import io.odin.Logger

object Main extends CMDApp(CLICommand()) {
  private given Logger[IO] = io.odin.consoleLogger[IO]()

  override def app(cmd: CLICommand): Stream[IO, Unit] = cmd match {
    case CLICommand.Extract(output, input, maxParallel) =>
      Extractor(
        input.fold(Storage.stdinResults)(Storage.load)
      )
    case CLICommand.Scrape(output, input, timeout, maxParallel) =>
      Scraper(
        input.fold(Storage.stdinSources)(Storage.sources),
        timeout,
        maxParallel,
        output
      )

  }

}
