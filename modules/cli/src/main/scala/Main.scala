package io.aibees.knowledgebase
package cli

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import fs2.Stream.*
import io.aibees.knowledgebase.Storage.stdinSources
import io.odin.Logger
import lepus.client.LepusClient
import org.http4s.ember.client.EmberClientBuilder

object Main extends CMDApp(CLICommand()) {
  protected given logger: Logger[IO] = io.odin.consoleLogger[IO]()

  override def app(cmd: CLICommand): Stream[IO, Unit] = cmd match {
    case CLICommand.Extract(output, inputs, children) =>
      val input =
        if inputs.isEmpty then Storage.stdinResults[WebsiteData]
        else emits(inputs).flatMap(Storage.load[WebsiteData])

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
        else emits(inputs).flatMap(Storage.sources)

      Scraper(
        input,
        output,
        cmd.config
      )
    case CLICommand.Sample(inputs, InputType.Scraped, output) =>
      val input =
        if inputs.isEmpty then Storage.stdinResults[WebsiteData]
        else emits(inputs).flatMap(Storage.load[WebsiteData])

      Sampling.scraped(input, output)

    case CLICommand.Sample(inputs, InputType.Extracted, output) =>
      val input =
        if inputs.isEmpty then Storage.stdinResults[ExperimentData]
        else emits(inputs).flatMap(Storage.load[ExperimentData])

      Sampling.extracted(input, output)

    case CLICommand.Inspect(inputs) =>
      val input =
        if inputs.isEmpty then Storage.stdinResults[WebsiteData]
        else emits(inputs).flatMap(Storage.load[WebsiteData])

      Inspect.scraped(input)

    case CLICommand.Service(cmd) =>
      cmd match {
        case ServiceCommand.Start(rmq, webPort, config) =>
          resource(connect(rmq)).flatMap { con =>
            val ws = webPort.fold(never[IO])(port =>
              exec(
                worker.Worker
                  .publisher(con)
                  .flatMap(worker.WebApplication(_, port))
                  .useForever
              )
            )
            worker.Worker(con, config).concurrently(ws)
          }
        case ServiceCommand.Upload(address, file, batchSize) =>
          resource(EmberClientBuilder.default[IO].build).flatMap(cl =>
            file
              .fold(stdinSources)(Storage.sources)
              .through(worker.WebApplication.upload(address, cl, batchSize))
          )
        case ServiceCommand.Submit(rmq, file, batchSize) =>
          val input = file.fold(stdinSources)(Storage.sources)

          resource(publisher(rmq))
            .flatMap(worker.Worker.submit(_, input, batchSize))
      }
  }

  private def connect(rmq: RabbitMQConfig) = LepusClient[IO](
    host = rmq.host,
    port = rmq.port,
    username = rmq.username,
    password = rmq.password
  )

  private def publisher(rmq: RabbitMQConfig) =
    connect(rmq).flatMap(worker.Worker.publisher(_))
}
