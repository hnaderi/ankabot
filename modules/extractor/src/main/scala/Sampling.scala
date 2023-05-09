package io.aibees.knowledgebase

import cats.Show
import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import fs2.Stream.*
import fs2.data.csv.CsvRowEncoder
import fs2.data.csv.Row
import fs2.data.csv.RowEncoder
import fs2.io.file.Path
import io.circe.Codec
import io.circe.KeyEncoder
import io.circe.derivation.ConfiguredEnumCodec
import io.circe.syntax.*
import io.odin.Logger

import java.net.URI
import scala.collection.MapView

object Sampling {

  def apply(input: Stream[IO, WebsiteData], output: Path)(using
      logger: Logger[IO]
  ): Stream[IO, Unit] = input
    .parEvalMapUnbounded(d =>
      IO { (d, d.home.result.flatMap(JsoupWebPage(_))) }
    )
    .scan(
      DataSample[SampleCategories, URI](100)(
        SampleCategories.values.toSeq: _*
      )
    ) { case (sample, (data, result)) =>
      import data.home.source
      val childCount = result.map(_.childPages.size).getOrElse(0)

      val childAdded = childCount match {
        case 0 => sample.add(SampleCategories.NoChild, source)
        case n if n >= 100 =>
          sample.add(SampleCategories.`100+ Children`, source)
        case n if n >= 50 =>
          sample.add(SampleCategories.`50-100 Children`, source)
        case n if n >= 25 =>
          sample.add(SampleCategories.`25-50 Children`, source)
        case other => sample
      }

      data.home.result match {
        case Left(FetchError.Timeout) =>
          childAdded.add(SampleCategories.Timeout, source)
        case Left(FetchError.Failed) =>
          childAdded.add(SampleCategories.Failed, source)
        case _ => childAdded
      }
    }
    .evalTap(d => logger.info(d.overview.toMap))
    .zipWithNext
    .collectFirst {
      case (a, _) if a.hasFinished => a
      case (a, None)               => a
    }
    .flatMap { sample =>
      val rows = sample.data.toVector.flatMap((cat, vs) =>
        vs.map(v => Row(NonEmptyList.of(cat.toString, v.toString)))
      )
      emits(rows)
    }
    .through(
      fs2.data.csv.encodeGivenHeaders(NonEmptyList.of("category", "url"))
    )
    .through(Storage.write(output))

  final case class SampledDataResults(
      timeout: Set[URI] = Set.empty
  )
  enum SampleCategories {
    case Failed, Timeout,
      NoChild,
      `25-50 Children`,
      `50-100 Children`,
      `100+ Children`,
  }

  object SampleCategories {
    given Codec[SampleCategories] = ConfiguredEnumCodec.derive()
    given KeyEncoder[SampleCategories] =
      KeyEncoder.encodeKeyString.contramap(_.toString)
    given Show[SampleCategories] = Show.fromToString
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

    def overview: MapView[K, Int] = data.mapValues(_.size)
  }

  object DataSample {
    def apply[K, V](max: Long)(categories: K*): DataSample[K, V] =
      new DataSample[K, V](
        max = max,
        remaining = max * categories.size,
        data = categories.map((_, Set.empty[V])).toMap
      )
  }

}
