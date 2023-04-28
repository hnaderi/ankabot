package io.aibees.knowledgebase

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.all.*
import com.monovore.decline.Argument
import com.monovore.decline.Command
import com.monovore.decline.Opts
import fs2.io.file.Path

import java.nio.file.{Path as JPath}
import scala.concurrent.duration.*

enum CLICommand {
  case Extract(
      output: Path,
      input: Option[Path] = None,
      maxParallel: Int = 10
  )
  case Scrape(
      output: Path,
      input: Option[Path] = None,
      timeout: FiniteDuration = 5.seconds,
      maxParallel: Int = 10
  )
}

object CLICommand {
  private given Argument[Path] = Argument[JPath].map(Path.fromNioPath)
  def apply(): Command[CLICommand] = Command("kb", "Knowledge base CLI")(
    Opts.subcommands(
      Command("extract", "Extract data") {
        (
          Opts.option[Path]("output", "Output file", "o"),
          Opts.option[Path]("input", "Input file", "i").orNone,
          Opts.option[Int]("max-parallel", "Max parallel", "n").withDefault(10),
        ).mapN(Extract(_, _, _))
      },
      Command("scrape", "Scrape sources") {
        (
          Opts.option[Path]("output", "Output file", "o"),
          Opts.option[Path]("input", "Input file", "i").orNone,
          Opts
            .option[FiniteDuration]("timeout", "Timeout", "t")
            .withDefault(5.seconds),
          Opts.option[Int]("max-parallel", "Max parallel", "n").withDefault(10),
        ).mapN(Scrape(_, _, _, _))
      }
    )
  )
}
