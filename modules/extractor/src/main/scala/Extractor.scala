package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import fs2.Stream.*
import fs2.io.file.Path
import io.circe.syntax.*
import io.odin.Logger

import java.net.URI
import scala.util.matching.Regex

object Extractor {

  def apply(input: Stream[IO, WebsiteData], output: Path, maxParallel: Int)(
      using logger: Logger[IO]
  ): Stream[IO, Unit] = for {
    metrics <- Metrics.printer()
    patterns <- eval(Technology.load)
    matcher = AllMatchers(patterns)
    _ <- input
      .parEvalMapUnordered(maxParallel) { fetched =>
        (fetched.home :: fetched.children)
          .traverse(getPage)
          .map(_.flatten)
          .flatMap {
            case Nil => logger.info(s"Skipping ${fetched.home.source}").as(None)
            case home :: children =>
              metrics
                .add(fetched.home, home.page.childPages.size)
                .as(
                  ExperimentData(
                    source = fetched.home.source,
                    contacts = Extractors.all(home.page).contacts,
                    technologies = matcher(home) ++ children.flatMap(matcher),
                    children = home.page.childPages
                  ).some
                )
          }
      }
      .unNone
      .through(Storage.persist(output))
  } yield ()

  private def getPage(fetch: FetchResult): IO[Option[ToExtract]] =
    fetch.result match {
      case Left(_) => IO(None)
      case Right(result) =>
        IO.blocking(JsoupWebPage(result))
          .map(_.toOption.map(ToExtract(_, result)))
    }
}

final case class ToExtract(
    page: WebPage,
    data: FetchedData
)

type TechnologyMatcher = ToExtract => Set[String]

final class AllMatchers(technologyMap: TechnologyMap)
    extends TechnologyMatcher {
  val technologies = technologyMap.toSet

  extension (reg: Regex) {
    private inline def matchesTo(value: String): Boolean =
      reg.findFirstIn(value).isDefined
  }

  private inline def matchesAny(
      values: Iterable[String],
      patterns: Set[Regex]
  ): Boolean = patterns.exists(p => values.exists(p.matchesTo(_)))

  private inline def matchesAny(value: String, patterns: Set[Regex]): Boolean =
    patterns.exists(_.matchesTo(value))

  private def matchesScriptSrc(page: WebPage, patterns: TechnologyPatterns) =
    matchesAny(page.scripts, patterns.scriptSrc)

  private def matchesURL(page: WebPage, patterns: TechnologyPatterns) =
    matchesAny(page.address.toString, patterns.url)

  private def matchesMeta(page: WebPage, patterns: TechnologyPatterns) =
    if page.metadata.isEmpty || patterns.meta.isEmpty then false
    else {
      val meta = page.metadata.map(m => (m.name, m.content)).toMap
      patterns.meta.exists((name, ps) =>
        meta.get(name).exists(matchesAny(_, ps))
      )
    }

  private def matchesHeader(data: FetchedData, patterns: TechnologyPatterns) =
    if data.headers.isEmpty || patterns.headers.isEmpty then false
    else {
      val headers = data.headers.toMap
      patterns.headers.exists((name, ps) =>
        headers.get(name).exists(matchesAny(_, ps))
      )
    }

  private def matchesCookies(data: FetchedData, patterns: TechnologyPatterns) =
    if data.cookies.isEmpty || patterns.cookies.isEmpty then false
    else {
      val cookies = data.cookies.map(c => (c.name, c.content)).toMap
      patterns.cookies.exists((name, ps) =>
        cookies.get(name).exists(matchesAny(_, ps))
      )
    }

  def apply(target: ToExtract): Set[String] =
    technologies
      .filter((name, tech) =>
        import tech.patterns
        matchesHeader(target.data, patterns) ||
        matchesCookies(target.data, patterns) ||
        matchesScriptSrc(target.page, patterns) ||
        matchesURL(target.page, patterns) ||
        matchesMeta(target.page, patterns)
      )
      .map(_._1)
}
