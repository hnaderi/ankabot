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
      children: Boolean = true
  )
  case Scrape(
      output: Path,
      inputs: List[Path] = Nil,
      timeout: FiniteDuration = 5.seconds,
      maxConcurrentPage: Int = 10,
      maxConcurrentFetch: Int = 30,
      maxChildren: Int = 0,
      backend: ScrapeBackend = ScrapeBackend.Ember
  )
  case Sample(
      inputs: List[Path] = Nil,
      output: Path
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
          Opts.flag("no-children", "Don't extract children").orTrue
        ).mapN(Extract(_, _, _))
      },
      Command("scrape", "Scrape sources") {
        (
          Opts.option[Path]("output", "Output file", "o"),
          Opts.arguments[Path]("input").orEmpty,
          Opts
            .option[FiniteDuration]("timeout", "Timeout", "t")
            .withDefault(5.seconds),
          Opts
            .option[Int]("max-page", "Max concurrent page", "n")
            .withDefault(10),
          Opts
            .option[Int]("max-fetch", "Max concurrent fetch")
            .withDefault(30),
          Opts
            .option[Int](
              "max-children",
              "How many child pages to get at maximum"
            )
            .withDefault(0),
          Opts
            .option[ScrapeBackend]("backend", "Scrape backend to use")
            .withDefault(ScrapeBackend.Ember)
        ).mapN(Scrape(_, _, _, _, _, _, _))
      },
      Command("sample", "Sample scraped data failures") {
        (
          Opts.arguments[Path]("input").orEmpty,
          Opts.option[Path]("output", "Output file", "o"),
        ).mapN(Sample(_, _))
      }
    )
  )
}
