package dev.hnaderi.ankabot

import cats.*
import cats.syntax.all.*
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder

import java.net.URI
import scala.concurrent.duration.*

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
    cookies: Set[Cookie],
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

private given Encoder[FiniteDuration] = Encoder[Long].contramap(_.toMillis)
private given Decoder[FiniteDuration] = Decoder[Long].map(_.millis)

final case class FetchResult(
    source: URI,
    result: Either[FetchError, FetchedData],
    time: FiniteDuration
) derives Codec.AsObject

final case class WebsiteData(
    time: FiniteDuration,
    home: FetchResult,
    children: List[FetchResult] = Nil
) derives Codec.AsObject
