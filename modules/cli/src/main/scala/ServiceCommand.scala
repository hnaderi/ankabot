package io.aibees.knowledgebase

import cats.syntax.all.*
import com.comcast.ip4s.*
import com.monovore.decline.Argument
import com.monovore.decline.Command
import com.monovore.decline.Opts
import fs2.io.file.Path

import java.net.URI
import java.nio.file.{Path as JPath}
import scala.concurrent.duration.*

enum ServiceCommand {
  case Start(
      rmq: RabbitMQConfig = RabbitMQConfig(),
      webPort: Option[Port] = None,
      maxConcurrent: Int = 10
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
        Command("start", "start a worker") {
          (
            RabbitMQConfig.opts,
            Opts.option[Port]("port", "web service listen port", "l").orNone,
            Opts
              .option[Int]("max-concurrent", "max concurrent processing", "N")
              .withDefault(10),
          ).mapN(Start(_, _, _))
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