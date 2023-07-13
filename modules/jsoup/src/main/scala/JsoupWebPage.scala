package dev.hnaderi.ankabot

import cats.syntax.all.*
import org.jsoup.Jsoup
import org.jsoup.nodes
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor

import java.net.URI
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

import Selector.*

final class JsoupWebPage private (doc: Document, val address: URI)
    extends WebPage {
  private def sel(selector: Selector) = (selector match {
    case Selector.XPath(value) => doc.selectXpath(value)
    case Selector.CSS(value)   => doc.select(value)
  }).asScala

  override def extract(selector: Selector, attr: String): Iterable[String] =
    sel(selector).map(_.attr(attr))

  override lazy val links: Set[Link] =
    sel(css"a")
      .map(a =>
        try {
          Link(a.text(), URI(a.absUrl("href"))).some
        } catch { case _ => None }
      )
      .collect { case Some(l) if l.value.isAbsolute => l }
      .toSet

  override lazy val title: String = doc.title()

  override lazy val comments: Set[String] = doc
    .head()
    .childNodes()
    .asScala
    .collect { case c: nodes.Comment => c }
    .map(_.getData())
    .toSet

  override lazy val styles: Set[String] = selectAttr("link", "href")
  override lazy val scripts: Set[String] = selectAttr("script", "src")

  private def selectAttr(selector: String, attr: String) = doc
    .select(selector)
    .asScala
    .map(_.attr(attr))
    .filterNot(_.isBlank)
    .toSet

  override lazy val texts: Iterable[String] = {
    val all = ListBuffer.empty[String]

    doc.traverse(new {
      override def head(node: Node, depth: Int): Unit = node match {
        case t: TextNode => all.addOne(t.getWholeText())
        case _           => ()
      }
    })
    all
  }

  override lazy val metadata: Set[PageMetadata] =
    sel(css"meta").collect { m =>
      (m.attr("name"), m.attr("content")) match {
        case (name, content) if !name.isBlank() || !content.isBlank() =>
          PageMetadata(name, content)
      }
    }.toSet

}

object JsoupWebPage {
  private final val maxAllowedSize = 5e6

  def apply(body: String, base: URI): Either[Throwable, JsoupWebPage] =
    Either
      .catchNonFatal {
        val bodySize = body.size
        if bodySize < maxAllowedSize then {
          val d = Jsoup.parse(body)
          d.setBaseUri(base.toString)
          d
        } else throw TooLarge(bodySize)
      }
      .map(new JsoupWebPage(_, base))

  def apply(stored: FetchedData): Either[Throwable, JsoupWebPage] =
    apply(stored.body, stored.url)

  final case class TooLarge(size: Long)
      extends Exception(
        s"Body size exceeds maximum allowed size! $size/$maxAllowedSize"
      )
}
