package dev.hnaderi.ankabot
package cli

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import fs2.Stream.*
import dev.hnaderi.ankabot.Storage.stdinSources
import dev.hnaderi.ankabot.db.PgConfig
import dev.hnaderi.ankabot.worker.Persistence
import io.odin.Logger
import lepus.client.LepusClient
import natchez.Trace.Implicits.noop
import org.http4s.ember.client.EmberClientBuilder
import skunk.Session
import skunk.util.Typer.Strategy

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
        case ServiceCommand.Migrate(pg) =>
          exec(Persistence.migrate(connect(pg).flatten))
        case ServiceCommand.Start(rmq, pg, webPort, config) =>
          val persist = connect(pg).map(Persistence(_))
          for {
            (db, con) <- resource(persist.parProduct(connect(rmq)))
            ws = webPort.fold(never[IO])(port =>
              exec(
                worker.Worker
                  .publisher(con)
                  .flatMap(worker.WebApplication(_, port))
                  .useForever
              )
            )
            extractor <- eval(Extractor.build())
            _ <- worker.Worker(con, db, config, extractor).concurrently(ws)
          } yield ()
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

  private def connect(pg: PgConfig) =
    logger.info(s"Connecting to postgres db: ${pg.database}").toResource *>
      Session.pooled[IO](
        host = pg.host,
        port = pg.port,
        user = pg.username,
        password = pg.password,
        database = pg.database,
        max = 10,
        strategy = Strategy.SearchPath
      )
}
