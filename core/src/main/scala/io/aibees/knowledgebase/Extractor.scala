package io.aibees.knowledgebase

import cats.effect.IO
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.jdk.CollectionConverters.*

object Extractor {
  def apply(body: String) = IO {
    val doc = Jsoup.parse(body)

    val scripts = select(doc, "script", "src")
    val styles = select(doc, "link", "href")

    println("Scripts:")
    scripts.foreach(println)

    println("Styles:")
    styles.foreach(println)
  }

  private def select(doc: Document, selector: String, attr: String) = doc
    .select(selector)
    .asScala
    .map(_.attr(attr))
    .filterNot(_.isBlank)
    .toList

}
