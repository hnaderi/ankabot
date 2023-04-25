package io.aibees.knowledgebase

import cats.syntax.all.*
import org.jsoup.Jsoup
import org.jsoup.nodes
import org.jsoup.nodes.Document
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

final class JsoupWebPage private (doc: Document) extends WebPage {

  override lazy val scripts: Set[String] = select(doc, "script", "src")

  override lazy val links: Set[String] = select(doc, "a", "href")

  override lazy val title: String = doc.title()

  override lazy val comments: Set[String] = doc
    .head()
    .childNodes()
    .asScala
    .collect { case c: nodes.Comment => c }
    .map(_.getData())
    .toSet

  override lazy val styles: Set[String] = select(doc, "link", "href")

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
    doc
      .select("meta")
      .asScala
      .collect { m =>
        (m.attr("name"), m.attr("content")) match {
          case (name, content) if !name.isBlank() || !content.isBlank() =>
            PageMetadata(name, content)
        }
      }
      .toSet

  private def select(doc: Document, selector: String, attr: String) = doc
    .select(selector)
    .asScala
    .map(_.attr(attr))
    .filterNot(_.isBlank)
    .toSet
}

object JsoupWebPage {
  def apply(body: String): Either[Throwable, JsoupWebPage] =
    Either.catchNonFatal(Jsoup.parse(body)).map(new JsoupWebPage(_))

  def apply(stored: PersistedData): Either[Throwable, JsoupWebPage] =
    apply(stored.raw.body)
}
