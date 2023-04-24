package io.aibees.knowledgebase

import cats.effect.IO
import cats.implicits.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.jdk.CollectionConverters.*
import org.jsoup.nodes

object Extractor {
  def apply(body: String) = IO {
    val doc = Jsoup.parse(body)

    val scripts = select(doc, "script", "src")
    val styles = select(doc, "link", "href")
    val links = select(doc, "a", "href")

    val metadata = doc.select("meta").asScala.collect { m =>
      (m.attr("name"), m.attr("content")) match {
        case (name, content) if !name.isBlank() || !content.isBlank() =>
          PageMetadata(name, content)
      }
    }

    val comments = doc
      .head()
      .childNodes()
      .asScala
      .collect { case c: nodes.Comment => c }
      .map(_.getData())

    ScrapedData(
      title = doc.title(),
      scripts = scripts,
      styles = styles,
      metadata = metadata.toSet,
      links = links,
      comments = comments.toSet
    )
  }

  private def select(doc: Document, selector: String, attr: String) = doc
    .select(selector)
    .asScala
    .map(_.attr(attr))
    .filterNot(_.isBlank)
    .toSet

}
