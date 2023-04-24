package io.aibees.knowledgebase

import java.net.URI
import cats.kernel.Monoid
import io.circe.Codec

enum Contact {
  case Email(value: String)
  case Phone(number: String)
  case Social(network: SocialNetwork, contact: URI)
}

enum SocialNetwork {
  case Linkedin,
    Telegram,
    Instagram,
    Twitter,
    Facebook,
    Youtube,
}

opaque type PageSource <: URI = URI

object PageSource {
  extension (self: PageSource) {
    def isHome: Boolean = {
      val path = self.getPath()
      path == null || path.isBlank()
    }
  }
}

final case class Cookie(
    name: String,
    content: String,
    domain: Option[String] = None,
    path: Option[String] = None
) derives Codec.AsObject

final case class RawScrapedData(
    headers: List[(String, String)], // TODO case insensitive
    status: Int,
    cookies: List[Cookie],
    body: String
) derives Codec.AsObject

enum ScrapeError {
  case Timeout
  case BadStatus(status: Int, message: Option[String] = None)
  case Failed
}

final case class ExtractedData(
    contacts: List[Contact] = Nil,
    technologies: List[String] = Nil
)

object ExtractedData {
  given Monoid[ExtractedData] = new {
    override def combine(x: ExtractedData, y: ExtractedData): ExtractedData =
      ExtractedData(
        contacts = x.contacts ::: y.contacts,
        technologies = x.technologies ::: y.technologies
      )

    override def empty: ExtractedData = ExtractedData()
  }
}

final case class PageMetadata(name: String, content: String)
    derives Codec.AsObject

final case class ScrapedData(
    title: String,
    scripts: Set[String] = Set.empty,
    styles: Set[String] = Set.empty,
    metadata: Set[PageMetadata] = Set.empty,
    links: Set[String] = Set.empty,
    comments: Set[String] = Set.empty
) derives Codec.AsObject

type DataExtractor = ScrapedData => ExtractedData
