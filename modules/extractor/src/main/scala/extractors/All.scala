package dev.hnaderi.ankabot
package extractors

import cats.effect.IO
import cats.syntax.all.*

final class All(patterns: TechnologyMap) extends DataExtractor {
  private val tech = TechnologyExtractor(patterns)
  def apply(target: ToExtract): ExtractedData =
    ContactExtractors.all(target) combine tech(target)

  def io(target: ToExtract): IO[ExtractedData] =
    IO(apply(target))
    // IO.cede.as(apply(target)).guarantee(IO.cede)
}
