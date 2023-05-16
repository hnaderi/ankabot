package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import io.odin.Logger

object Main extends CMDApp(CLICommand()) {
  protected given logger: Logger[IO] = io.odin.consoleLogger[IO]()

  override def app(cmd: CLICommand): Stream[IO, Unit] = cmd match {
    case CLICommand.Extract(output, inputs, children) =>
      val input =
        if inputs.isEmpty then Storage.stdinResults[WebsiteData]
        else Stream.emits(inputs).flatMap(Storage.load[WebsiteData])

      Extractor(
        input = input,
        output = output,
        maxParallel = 3 * this.computeWorkerThreadCount / 4,
        extractChild = children
      )
    case cmd: CLICommand.Scrape =>
      import cmd.*
      val input =
        if inputs.isEmpty then Storage.stdinSources
        else Stream.emits(inputs).flatMap(Storage.sources)

      Scraper(
        input,
        timeout,
        maxConcurrentPage = maxConcurrentPage,
        maxConcurrentFetch = maxConcurrentFetch,
        maxChildren = maxChildren,
        backend = backend,
        output
      )
    case CLICommand.Sample(inputs, output) =>
      val input =
        if inputs.isEmpty then Storage.stdinResults[WebsiteData]
        else Stream.emits(inputs).flatMap(Storage.load[WebsiteData])

      Sampling(input, output)

  }

}
