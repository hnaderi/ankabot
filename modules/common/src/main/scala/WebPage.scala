package io.aibees.knowledgebase

trait WebPage {
  def title: String
  def scripts: Set[String]
  def styles: Set[String]
  def metadata: Set[PageMetadata]
  def links: Set[Link]
  def comments: Set[String]

  def texts: Iterable[String]
}

object WebPage {
  extension (page: WebPage) {
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
  }
}
