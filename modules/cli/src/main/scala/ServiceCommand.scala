package dev.hnaderi.ankabot
package cli

import cats.syntax.all.*
import com.comcast.ip4s.*
import com.monovore.decline.Argument
import com.monovore.decline.Command
import com.monovore.decline.Opts
import dev.hnaderi.ankabot.db.PgConfig
import dev.hnaderi.ankabot.extractors.ExtractorConfig
import fs2.io.file.Path

import java.net.URI

enum ServiceCommand {
  case Migrate(
      pg: PgConfig = PgConfig()
  )
  case Start(
      rmq: RabbitMQConfig = RabbitMQConfig(),
      pg: PgConfig = PgConfig(),
      webPort: Option[Port] = None,
      config: Scraper.Config = Scraper.Config(),
      s3: S3Config = S3Config.Disabled,
      extractor: ExtractorConfig = ExtractorConfig.default
  )
  case Upload(
      url: URI,
      file: Option[Path] = None,
      batchSize: Int = 10
  )
  case Submit(
      rmq: RabbitMQConfig = RabbitMQConfig(),
      file: Option[Path] = None,
      batchSize: Int = 10
  )
}

object ServiceCommand {
  private val batchSize = Opts
    .option[Int]("batch-size", "Batch size for each task", "s")
    .withDefault(10)
  private val file = Opts.option[Path]("file", "file to submit", "f").orNone

  def apply(): Command[ServiceCommand] =
    Command("service", "Distributed service")(
      Opts.subcommands(
        Command("migrate", "migrate db") { PgConfig.opts.map(Migrate(_)) },
        Command("start", "start a worker") {
          (
            RabbitMQConfig.opts,
            PgConfig.opts,
            Opts.option[Port]("port", "web service listen port", "l").orNone,
            CLICommand.scrapeConfig,
            S3Config.opts,
            CLICommand.extractConfig
          ).mapN(Start(_, _, _, _, _, _))
        },
        Command("upload", "upload to a submission request") {
          (
            Opts
              .argument[URI]("server-address")
              .withDefault(URI("http://localhost:8080/")),
            file,
            batchSize,
          ).mapN(Upload(_, _, _))
        },
        Command("submit", "submit directly to the task pool") {
          (RabbitMQConfig.opts, file, batchSize).mapN(Submit(_, _, _))
        }
      )
    )
}
