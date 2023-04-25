package io.aibees.knowledgebase

import java.net.URI
import cats.kernel.Monoid
import io.circe.Codec
import cats.syntax.all.*
import cats.Functor
import cats.Traverse
import cats.Applicative
import cats.Eval
import cats.Show

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

final case class PersistedResult(
    raw: RawScrapedData,
    data: ScrapedData
) derives Codec.AsObject

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

final case class Statistics(
    total: Long = 0,
    ok: Long = 0,
    failed: Long = 0,
    timedout: Long = 0,
    byStatus: Map[Int, Long] = Map.empty
) {
  private final def addStatus(value: Int) = byStatus.updatedWith(value) {
    case None        => Some(1L)
    case Some(value) => Some(value + 1L)
  }

  final def add(result: ScrapeResult[RawScrapedData]): Statistics =
    result.result match {
      case Right(value) =>
        copy(total = total + 1, ok = ok + 1, byStatus = addStatus(value.status))
      case Left(ScrapeError.Timeout) =>
        copy(total = total + 1, timedout = timedout + 1)
      case Left(ScrapeError.Failed) =>
        copy(total = total + 1, failed = failed + 1)
      case Left(ScrapeError.BadStatus(status, _)) =>
        copy(total = total + 1, byStatus = addStatus(status))
    }
}
object Statistics {
  given Show[Statistics] = Show.show(st => s"""OK: ${st.ok}
Failed: ${st.failed}
Timedout: ${st.timedout}
${st.byStatus.map((k, v) => s"$k: $v").mkString("\n")}

Total: ${st.total}
""")
}
