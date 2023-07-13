package dev.hnaderi.ankabot
package extractors

import cats.effect.IO
import cats.syntax.all.*

object Builder {
  private val empty: Extractor = _ => IO(ExtractedData.empty)
  private val icons: Extractor = x => IO(ExtractedData(icons = x.page.icons))
  def apply(
      config: ExtractorConfig = ExtractorConfig.default
  ): IO[Extractor] = {
    val allTech = Technology.load.map(TechnologyExtractor(_))
    val allContacts: Extractor = x => IO(ContactExtractors.all(x))

    val dynamic =
      if !config.noContacts && !config.noTechnologies then
        allTech.map(tx => allContacts and tx)
      else if !config.noContacts then IO(allContacts)
      else if !config.noTechnologies then allTech
      else IO(empty)

    dynamic.map((x: Extractor) => x and icons)
  }
}

final case class ExtractorConfig(
    noTechnologies: Boolean = false,
    noContacts: Boolean = false
)

object ExtractorConfig {
  val default: ExtractorConfig = ExtractorConfig()
}
