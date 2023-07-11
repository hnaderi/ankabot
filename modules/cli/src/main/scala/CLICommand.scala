package dev.hnaderi.ankabot
package cli

import cats.syntax.all.*
import com.monovore.decline.Argument
import com.monovore.decline.Command
import com.monovore.decline.Opts
import dev.hnaderi.ankabot.extractors.ExtractorConfig
import fs2.io.file.Path

import scala.concurrent.duration.*

enum CLICommand {
  case Extract(
      output: Path,
      inputs: InputPath = InputPath.StdIn,
      children: Boolean = true,
      config: ExtractorConfig = ExtractorConfig.default
  )
  case ExtractRaw(
      output: Path,
      inputs: InputPath = InputPath.StdIn,
      config: ExtractorConfig = ExtractorConfig.default
  )
  case Scrape(
      output: Path,
      inputs: InputPath = InputPath.StdIn,
      config: Scraper.Config
  )
  case Inspect(
      inputs: List[Path] = Nil
  )
  case Service(cmd: ServiceCommand)
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

  private[cli] val extractConfig: Opts[ExtractorConfig] = (
    Opts.flag("no-contacts", "don't extract contacts").orFalse,
    Opts.flag("no-technologies", "don't extract technologies").orFalse,
  ).mapN((c, t) => ExtractorConfig(noContacts = c, noTechnologies = t))

  def apply(): Command[CLICommand] = Command("ankabot", "Ankabot CLI")(
    Opts.subcommands(
      Command("extract", "Extract data") {
        (
          Opts.option[Path]("output", "Output file", "o"),
          InputPath.opts,
          Opts.flag("no-children", "Don't extract children").orTrue,
          extractConfig
        ).mapN(Extract(_, _, _, _))
      },
      Command("extract-raw", "Extract from raw data") {
        (
          Opts.option[Path]("output", "Output file", "o"),
          InputPath.opts,
          extractConfig
        ).mapN(ExtractRaw(_, _, _))
      },
      Command("scrape", "Scrape sources") {
        (
          Opts.option[Path]("output", "Output file", "o"),
          InputPath.opts,
          scrapeConfig
        ).mapN(Scrape(_, _, _))
      },
      Command("inspect", "Inspect data") {
        Opts.arguments[Path]("input").orEmpty.map(Inspect(_))
      },
      ServiceCommand().map(Service(_))
    )
  )
}
