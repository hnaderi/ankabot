package dev.hnaderi.ankabot
package extractors

import cats.effect.IO
import cats.syntax.all.*

import scala.util.matching.Regex

private final class TechnologyExtractor(technologyMap: TechnologyMap)
    extends Extractor {
  val technologies =
    technologyMap
      .filter((_, v) => v.patterns != TechnologyPatterns.empty)
      .toList

  extension (reg: Regex) {
    private inline def matchesTo(value: String): Boolean =
      reg.pattern.matcher(value).find()
  }

  private inline def matchesAny(
      values: Iterable[String],
      patterns: Set[Regex]
  ): Boolean = patterns.exists(p => values.exists(p.matchesTo(_)))

  private inline def matchesAny(value: String, patterns: Set[Regex]): Boolean =
    patterns.exists(_.matchesTo(value))

  private def matchesScriptSrc(page: WebPage, patterns: TechnologyPatterns) =
    matchesAny(
      page.scripts.view.filterNot(s => s.startsWith("data:") || s.size > 1000),
      patterns.scriptSrc
    )

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

  def apply(target: ToExtract): IO[ExtractedData] =
    technologies
      .traverseFilter { case (name, tech) =>
        import tech.patterns
        IO.blocking {
          Option.when(
            matchesHeader(target.data, patterns) ||
              matchesCookies(target.data, patterns) ||
              matchesScriptSrc(target.page, patterns) ||
              matchesURL(target.page, patterns) ||
              matchesMeta(target.page, patterns)
          )(name)
        }
      }
      .map(names => ExtractedData(technologies = names.toSet))

}
