package dev.hnaderi.ankabot

import java.net.URI

trait WebPage {
  def address: URI
  def title: String
  def scripts: Set[String]
  def styles: Set[String]
  def metadata: Set[PageMetadata]
  def links: Set[Link]
  def comments: Set[String]
  def icons: Set[String]

  def texts: Iterable[String]
}

object WebPage {
  extension (page: WebPage) {
    inline def resolve(url: String): URI = page.address.resolve(url)
    inline def resolve(url: URI): URI = page.address.resolve(url)

    def toPersistedData(raw: FetchedData): PersistedData = new PersistedData(
      raw,
      ScrapedData(
        scripts = page.scripts,
        styles = page.styles,
        metadata = page.metadata,
        links = page.links,
        comments = page.comments
      )
    )

    def childPages: Set[URI] = {
      val base = page.address.resolve("/")
      page.links
        .map(l => base.relativize(l.value))
        .filterNot(uri => uri.isAbsolute || uri.getPath.isBlank)
        .map(base.resolve)
    }
  }
}
