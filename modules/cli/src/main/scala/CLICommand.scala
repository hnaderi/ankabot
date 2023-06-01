package io.aibees.knowledgebase
package cli

import cats.syntax.all.*
import com.monovore.decline.Argument
import com.monovore.decline.Command
import com.monovore.decline.Opts
import fs2.io.file.Path

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
      config: Scraper.Config
  )
  case Sample(
      inputs: List[Path] = Nil,
      inputType: InputType = InputType.Scraped,
      output: Path
  )
  case Inspect(
      inputs: List[Path] = Nil
  )
  case Service(cmd: ServiceCommand)
}

enum InputType {
  case Scraped, Extracted
}

object InputType {
  given Argument[InputType] = Argument.fromMap(
    "file type",
    InputType.values.map(b => b.toString.toLowerCase -> b).toMap
  )
}

object CLICommand {
  private given Argument[ScrapeBackend] = Argument.fromMap(
    "scrape backend",
    ScrapeBackend.values.map(b => b.toString.toLowerCase -> b).toMap
  )
  private[cli] val scrapeConfig: Opts[Scraper.Config] = (
    Opts
      .option[FiniteDuration]("timeout", "Timeout", "t")
      .withDefault(5.seconds),
    Opts
      .option[Short]("max-page", "Max concurrent page", "n")
      .withDefault[Short](10),
    Opts
      .option[Short]("max-fetch", "Max concurrent fetch")
      .withDefault[Short](30),
    Opts
      .option[Short](
        "max-children",
        "How many child pages to get at maximum"
      )
      .withDefault[Short](0),
    Opts
      .option[Short](
        "max-redirect",
        "How many redirects to follow at maximum"
      )
      .withDefault[Short](5),
    Opts
      .option[ScrapeBackend]("backend", "Scrape backend to use")
      .withDefault(ScrapeBackend.JDK)
  ).mapN(Scraper.Config(_, _, _, _, _, _))

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
          scrapeConfig
        ).mapN(Scrape(_, _, _))
      },
      Command("sample", "Sample scraped data failures") {
        (
          Opts.arguments[Path]("input").orEmpty,
          Opts
            .option[InputType]("type", "File type")
            .withDefault(InputType.Scraped),
          Opts.option[Path]("output", "Output file", "o"),
        ).mapN(Sample(_, _, _))
      },
      Command("inspect", "Inspect data") {
        Opts.arguments[Path]("input").orEmpty.map(Inspect(_))
      },
      ServiceCommand().map(Service(_))
    )
  )
}
