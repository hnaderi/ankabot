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

final case class RawScrapedData(
    url: URI,
    headers: List[(String, String)], // TODO case insensitive
    status: Int,
    cookies: List[Cookie],
    body: String
) derives Codec.AsObject

enum ScrapeError derives Codec.AsObject {
  case Timeout
  case BadStatus(status: Int, message: Option[String] = None)
  case Failed
}

final case class PageMetadata(name: String, content: String)
    derives Codec.AsObject

final case class Link(text: String, value: URI) derives Codec.AsObject

final case class ScrapedData(
    title: String,
    scripts: Set[String] = Set.empty,
    styles: Set[String] = Set.empty,
    metadata: Set[PageMetadata] = Set.empty,
    links: Set[Link] = Set.empty,
    comments: Set[String] = Set.empty
) derives Codec.AsObject

final case class PersistedData(
    raw: RawScrapedData,
    data: ScrapedData
) derives Codec.AsObject

object PersistedData {
  def apply(
      raw: RawScrapedData,
      page: WebPage
  ): PersistedData = new PersistedData(
    raw,
    ScrapedData(
      title = page.title,
      scripts = page.scripts,
      styles = page.styles,
      metadata = page.metadata,
      links = page.links,
      comments = page.comments
    )
  )
}

type PersistedResult = ScrapeResult[PersistedData]

final case class ScrapeResult[T](
    source: URI,
    result: Either[ScrapeError, T]
) {
  final def map[B](f: T => B): ScrapeResult[B] = copy(result = result.map(f))
}

object ScrapeResult {
  given [T: Codec]: Codec[ScrapeResult[T]] =
    io.circe.generic.semiauto.deriveCodec

  given Functor[ScrapeResult] = new {
    override def map[A, B](fa: ScrapeResult[A])(f: A => B): ScrapeResult[B] =
      fa.map(f)
  }

  given Traverse[ScrapeResult] = new {

    override def foldLeft[A, B](fa: ScrapeResult[A], b: B)(f: (B, A) => B): B =
      fa.result.foldLeft(b)(f)

    override def foldRight[A, B](fa: ScrapeResult[A], lb: Eval[B])(
        f: (A, Eval[B]) => Eval[B]
    ): Eval[B] = fa.result.foldRight(lb)(f)

    override def traverse[G[_]: Applicative, A, B](fa: ScrapeResult[A])(
        f: A => G[B]
    ): G[ScrapeResult[B]] =
      fa.result.traverse(f).map(res => fa.copy(result = res))

  }
}
