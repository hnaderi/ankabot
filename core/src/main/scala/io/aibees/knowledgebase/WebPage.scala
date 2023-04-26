package io.aibees.knowledgebase

trait WebPage {
  def title: String
  def scripts: Set[String]
  def styles: Set[String]
  def metadata: Set[PageMetadata]
  def links: Set[String]
  def comments: Set[String]

  def texts: Iterable[String]
}

trait HomePage extends WebPage {
  def aboutPage: Option[WebPage]
  def contactPage: Option[WebPage]
}
