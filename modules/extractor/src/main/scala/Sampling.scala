package dev.hnaderi.ankabot

import cats.Show
import cats.data.NonEmptyList
import cats.effect.IO
import fs2.Pipe
import fs2.Stream
import fs2.Stream.*
import fs2.data.csv.Row
import io.circe.Codec
import io.circe.KeyEncoder
import io.circe.derivation.ConfiguredEnumCodec
import io.odin.Logger

import java.net.URI
import scala.collection.MapView

object Sampling {

  def scraped(input: Stream[IO, WebsiteData])(using
      logger: Logger[IO]
  ): Stream[IO, String] = input
    .parEvalMapUnbounded(d =>
      IO { (d, d.home.result.flatMap(JsoupWebPage(_))) }
    )
    .through(
      uriSampler(ScrapedCategories.values) { case (sample, (data, result)) =>
        import data.home.source
        val childCount = result.map(_.childPages.size).getOrElse(0)

        val childAdded = childCount match {
          case 0 => sample.add(ScrapedCategories.NoChild, source)
          case n if n >= 100 =>
            sample.add(ScrapedCategories.`100+ Children`, source)
          case n if n >= 50 =>
            sample.add(ScrapedCategories.`50-100 Children`, source)
          case n if n >= 25 =>
            sample.add(ScrapedCategories.`25-50 Children`, source)
          case other => sample
        }

        data.home.result match {
          case Left(FetchError.Timeout) =>
            childAdded.add(ScrapedCategories.Timeout, source)
          case Left(FetchError.Failed) =>
            childAdded.add(ScrapedCategories.Failed, source)
          case _ => childAdded
        }
      }
    )
    .through(writeCSV)

  def extracted(input: Stream[IO, ExperimentData])(using
      logger: Logger[IO]
  ): Stream[IO, String] = input
    .through(
      uriSampler(ExtractedCategories.values) { case (sample, data) =>
        import ExtractedCategories.*
        if data.technologies.isEmpty then sample.add(NoTechnology, data.source)
        else if data.contacts.isEmpty then sample.add(NoContacts, data.source)
        else sample
      }
    )
    .through(writeCSV)

  private def uriSampler[D, K: Show](cats: Iterable[K], size: Int = 100)(
      handle: (DataSample[K, URI], D) => DataSample[K, URI]
  )(using Logger[IO]): Pipe[IO, D, DataSample[K, URI]] =
    sampler(DataSample[K, URI](size)(cats))(handle)

  private def sampler[D, K: Show, V](init: DataSample[K, V])(
      handle: (DataSample[K, V], D) => DataSample[K, V]
  )(using logger: Logger[IO]): Pipe[IO, D, DataSample[K, V]] =
    _.scan(init)(handle)
      .evalTap(d => logger.info(d.overview.toMap))
      .zipWithNext
      .collectFirst {
        case (a, _) if a.hasFinished => a
        case (a, None)               => a
      }

  private def writeCSV[K: Show]: Pipe[IO, DataSample[K, URI], String] =
    _.flatMap { sample =>
      val rows = sample.data.toVector.flatMap((cat, vs) =>
        vs.map(v => Row(NonEmptyList.of(cat.toString, v.toString)))
      )
      emits(rows)
    }
      .through(
        fs2.data.csv.encodeGivenHeaders(NonEmptyList.of("category", "url"))
      )

  final case class SampledDataResults(
      timeout: Set[URI] = Set.empty
  )
  enum ScrapedCategories {
    case Failed, Timeout,
      NoChild,
      `25-50 Children`,
      `50-100 Children`,
      `100+ Children`,
  }

  object ScrapedCategories {
    given Codec[ScrapedCategories] = ConfiguredEnumCodec.derive()
    given KeyEncoder[ScrapedCategories] =
      KeyEncoder.encodeKeyString.contramap(_.toString)
    given Show[ScrapedCategories] = Show.fromToString
  }

  enum ExtractedCategories {
    case NoContacts, NoTechnology
  }

  object ExtractedCategories {
    given Codec[ExtractedCategories] = ConfiguredEnumCodec.derive()
    given KeyEncoder[ExtractedCategories] =
      KeyEncoder.encodeKeyString.contramap(_.toString)
    given Show[ExtractedCategories] = Show.fromToString
  }

  final class DataSample[K, V] private (
      max: Long,
      val remaining: Long,
      val data: Map[K, Set[V]]
  ) {
    def add(category: K, v: V): DataSample[K, V] = {
      val (newData, added) = data.get(category) match {
        case None if max > 0 =>
          (data.updated(category, Set(v)), 1)
        case Some(value) if value.size < max =>
          (data.updated(category, value + v), 1)
        case _ => (data, 0)
      }
      new DataSample(
        data = newData,
        max = max,
        remaining = remaining - added
      )
    }

    def hasFinished: Boolean = remaining <= 0

    def overview: MapView[K, Int] = data.view.mapValues(_.size)
  }

  object DataSample {
    def apply[K, V](max: Long)(categories: Iterable[K]): DataSample[K, V] =
      new DataSample[K, V](
        max = max,
        remaining = max * categories.size,
        data = categories.map((_, Set.empty[V])).toMap
      )
  }

}
