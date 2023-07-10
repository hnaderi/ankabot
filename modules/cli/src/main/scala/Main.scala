package dev.hnaderi.ankabot
package cli

import cats.effect.IO
import cats.syntax.all.*
import dev.hnaderi.ankabot.Storage.stdinSources
import dev.hnaderi.ankabot.db.PgConfig
import dev.hnaderi.ankabot.worker.DBPersistence
import dev.hnaderi.ankabot.worker.Persistence
import fs2.Stream
import fs2.Stream.*
import io.odin.Logger
import lepus.client.Connection.Status
import lepus.client.LepusClient
import natchez.Trace.Implicits.noop
import org.http4s.ember.client.EmberClientBuilder
import skunk.Session
import skunk.util.Typer.Strategy

object Main extends CMDApp(CLICommand()) {
  protected given logger: Logger[IO] = io.odin.consoleLogger[IO]()

  override def app(cmd: CLICommand): Stream[IO, Unit] = cmd match {
    case CLICommand.Extract(output, inputs, children) =>
      val input = inputs.read.through(Storage.decodeJsonline[WebsiteData])

      Extractor(
        input = input,
        maxParallel = 3 * this.computeWorkerThreadCount / 4,
        extractChild = children
      ).through(Storage.persist(output))

    case CLICommand.ExtractRaw(output, inputs) =>
      val input = inputs.read.through(Storage.decodeJsonline[RawData])

      ExtractorRaw(
        input = input,
        maxParallel = 3 * this.computeWorkerThreadCount / 4
      ).through(Storage.persist(output))

    case cmd: CLICommand.Scrape =>
      import cmd.*
      val input =
        inputs.read.through(fs2.text.utf8.decode).through(Helpers.decodeSources)

      Scraper(input, cmd.config).through(Storage.persist(output))

    case CLICommand.Inspect(inputs) =>
      val input =
        if inputs.isEmpty then Storage.stdinResults[WebsiteData]
        else emits(inputs).flatMap(Storage.load[WebsiteData])

      Inspect.scraped(input)

    case CLICommand.Service(cmd) =>
      cmd match {
        case ServiceCommand.Migrate(pg) =>
          exec(DBPersistence.migrate(connect(pg).flatten))
        case ServiceCommand.Start(rmq, pg, webPort, config, s3) =>
          for {
            (persist, upload) <- Persistence(connect(pg), s3)
            con <- resource(connect(rmq))
            ws = webPort.fold(never[IO])(port =>
              exec(
                worker.JobRunner
                  .publisher(con)
                  .flatMap(worker.WebApplication(_, port))
                  .useForever
              )
            )
            extractor <- eval(Extractor.build())
            _ <- worker
              .Worker(con, persist, config, extractor)
              .concurrently(ws)
              .concurrently(upload)
              .concurrently(
                con.status.discrete.foreach(s => logger.debug(s"RMQ: $s"))
              )
              .interruptWhen(con.status.map(_ == Status.Closed))
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
            .flatMap(worker.JobRunner.submit(_, input, batchSize))
      }
  }

  private def connect(rmq: RabbitMQConfig) = LepusClient[IO](
    host = rmq.host,
    port = rmq.port,
    username = rmq.username,
    password = rmq.password
  )

  private def publisher(rmq: RabbitMQConfig) =
    connect(rmq).flatMap(worker.JobRunner.publisher(_))

  private def connect(pg: PgConfig) =
    logger.info(s"Connecting to postgres db: ${pg.database}").toResource *>
      Session.pooled[IO](
        host = pg.host,
        port = pg.port,
        user = pg.username,
        password = pg.password,
        database = pg.database,
        max = pg.poolSize,
        strategy = Strategy.SearchPath
      )
}
