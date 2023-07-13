package dev.hnaderi.ankabot

import cats.syntax.all.*

import java.net.URI
import java.net.URISyntaxException

import Selector.*

trait WebPage {
  def address: URI
  def title: String
  def scripts: Set[String]
  def styles: Set[String]
  def metadata: Set[PageMetadata]
  def links: Set[Link]
  def comments: Set[String]

  def extract(selector: Selector, attr: String): Iterable[String]

  def texts: Iterable[String]
}

object WebPage {
  extension (page: WebPage) {
    inline def resolve(url: String): URI = {
      Either.catchNonFatal(page.address.resolve(url)) match {
        case Right(value) => value
        case Left(_: URISyntaxException) =>
          val fixed = url.replace(' ', '+')
          Either
            .catchNonFatal(page.address.resolve(fixed))
            .getOrElse(page.address)
        case _ => page.address
      }
    }
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

    def icons: Set[String] = page
      .extract(css"""link[rel*="icon"]""", "href")
      .map(resolve(_).toString)
      .filterNot(_.isBlank)
      .toSet

    def logos: Set[String] = page
      .extract(
        xpath"//*[self::footer or self::header]//*[@id[contains(., 'logo')] or @class[contains(., 'logo')]]//img",
        "src"
      )
      .map(resolve(_).toString)
      .filterNot(_.isBlank)
      .toSet
  }
}

enum Selector {
  case XPath(value: String)
  case CSS(value: String)
}

object Selector {
  extension (sc: StringContext) {
    def xpath(args: Any*): XPath = XPath(sc.s(args: _*))
    def css(args: Any*): CSS = CSS(sc.s(args: _*))
  }
}
