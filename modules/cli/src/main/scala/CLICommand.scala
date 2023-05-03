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
      inputs: List[Path] = Nil,
      maxParallel: Int = 10
  )
  case Scrape(
      output: Path,
      input: List[Path] = Nil,
      timeout: FiniteDuration = 5.seconds,
      maxParallel: Int = 10
  )
  case Stat(
      inputs: List[Path] = Nil
  )
}

object CLICommand {
  private given Argument[Path] = Argument[JPath].map(Path.fromNioPath)
  def apply(): Command[CLICommand] = Command("kb", "Knowledge base CLI")(
    Opts.subcommands(
      Command("extract", "Extract data") {
        (
          Opts.option[Path]("output", "Output file", "o"),
          Opts.arguments[Path]("input").orEmpty,
          Opts.option[Int]("max-parallel", "Max parallel", "n").withDefault(10),
        ).mapN(Extract(_, _, _))
      },
      Command("scrape", "Scrape sources") {
        (
          Opts.option[Path]("output", "Output file", "o"),
          Opts.arguments[Path]("input").orEmpty,
          Opts
            .option[FiniteDuration]("timeout", "Timeout", "t")
            .withDefault(5.seconds),
          Opts.option[Int]("max-parallel", "Max parallel", "n").withDefault(10),
        ).mapN(Scrape(_, _, _, _))
      },
      Command("stats", "Statistics for scraped data") {
        Opts.arguments[Path]("input").orEmpty.map(Stat(_))
      }
    )
  )
}
