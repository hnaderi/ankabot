package io.aibees.knowledgebase

import cats.*
import cats.syntax.all.*
import io.circe.Codec

import java.net.URI

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

final case class HttpVersion(major: Int, minor: Int) derives Codec.AsObject

final case class FetchedData(
    url: URI,
    headers: List[(String, String)], // TODO case insensitive
    status: Int,
    httpVersion: HttpVersion,
    cookies: List[Cookie],
    body: String
) derives Codec.AsObject

enum FetchError derives Codec.AsObject {
  case Timeout
  case BadStatus(status: Int, message: Option[String] = None)
  case Failed
}

final case class PageMetadata(name: String, content: String)
    derives Codec.AsObject

final case class Link(text: String, value: URI) derives Codec.AsObject

final case class ScrapedData(
    scripts: Set[String] = Set.empty,
    styles: Set[String] = Set.empty,
    metadata: Set[PageMetadata] = Set.empty,
    links: Set[Link] = Set.empty,
    comments: Set[String] = Set.empty
) derives Codec.AsObject

final case class PersistedData(
    raw: FetchedData,
    data: ScrapedData
) derives Codec.AsObject

type PersistedResult = FetchResult

final case class FetchResult(
    source: URI,
    result: Either[FetchError, FetchedData]
) derives Codec.AsObject
