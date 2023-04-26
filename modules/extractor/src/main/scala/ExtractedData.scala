package io.aibees.knowledgebase

import io.circe.Codec
import cats.Monoid

final case class ExtractedData(
    contacts: Set[Contact] = Set.empty,
    technologies: Set[String] = Set.empty
) derives Codec.AsObject

object ExtractedData {
  given Monoid[ExtractedData] = new {
    override def combine(x: ExtractedData, y: ExtractedData): ExtractedData =
      ExtractedData(
        contacts = x.contacts ++ y.contacts,
        technologies = x.technologies ++ y.technologies
      )

    override def empty: ExtractedData = ExtractedData()
  }
}

type DataExtractor = WebPage => ExtractedData
