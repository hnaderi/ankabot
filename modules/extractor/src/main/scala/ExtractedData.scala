package dev.hnaderi.ankabot

import cats.Monoid
import io.circe.Codec

final case class ExtractedData(
    contacts: Set[Contact] = Set.empty,
    technologies: Set[String] = Set.empty
) derives Codec.AsObject

object ExtractedData {
  val empty: ExtractedData = ExtractedData()
  given Monoid[ExtractedData] = new {
    override def combine(x: ExtractedData, y: ExtractedData): ExtractedData =
      ExtractedData(
        contacts = x.contacts ++ y.contacts,
        technologies = x.technologies ++ y.technologies
      )

    override def empty: ExtractedData = ExtractedData.empty
  }
}

type DataExtractor = ToExtract => ExtractedData
