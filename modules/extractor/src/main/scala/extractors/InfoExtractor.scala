package dev.hnaderi.ankabot
package extractors

import cats.syntax.all.*

object InfoExtractor {
  def apply(page: WebPage): WebsiteInfoData = WebsiteInfoData(
    icons = page.icons,
    title = page.title.some,
    name = page.getMeta("og:title", "og:site_name"),
    description = page.getMeta("og:description", "description"),
    image = page.getMeta("og:image").map(page.resolve(_)),
    locale = page.getMeta("og:locale")
  )

  extension (page: WebPage) {
    private inline def getMeta(inline ns: String*): Option[String] =
      page.metadata
        .find(m => ns.contains(m.name.toLowerCase))
        .map(_.content)
        .filterNot(_.isBlank)
  }
}
