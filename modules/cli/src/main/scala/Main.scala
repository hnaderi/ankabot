package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import io.odin.Logger

object Main extends CMDApp(CLICommand()) {
  private given Logger[IO] = io.odin.consoleLogger[IO]()

  override def app(cmd: CLICommand): Stream[IO, Unit] = cmd match {
    case CLICommand.Extract(output, inputs, maxParallel) =>
      val input =
        if inputs.isEmpty then Storage.stdinResults[FetchResult]
        else Stream.emits(inputs).flatMap(Storage.load[FetchResult])

      Extractor(
        input = input,
        output = output,
        maxParallel = maxParallel
      )
    case CLICommand.Scrape(output, inputs, timeout, maxParallel) =>
      val input =
        if inputs.isEmpty then Storage.stdinSources
        else Stream.emits(inputs).flatMap(Storage.sources)

      Scraper(
        input,
        timeout,
        maxParallel,
        output
      )
    case CLICommand.Stat(inputs) => ???

  }

}
