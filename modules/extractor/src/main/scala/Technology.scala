package dev.hnaderi.ankabot

import cats.effect.IO
import io.circe.*
import io.circe.derivation.ConfiguredEnumCodec

import scala.util.matching.Regex

final case class Technology(
    description: Option[String] = None,
    icon: Option[String] = None,
    website: Option[String] = None,
    saas: Option[Boolean] = None,
    oss: Option[Boolean] = None,
    cpe: Option[String] = None,
    pricing: Set[Pricing] = Set.empty,
    implies: Set[String] = Set.empty,
    requires: Set[String] = Set.empty,
    requiresCategory: Set[Int] = Set.empty,
    excludes: Set[String] = Set.empty,
    patterns: TechnologyPatterns = TechnologyPatterns.empty
) derives Codec.AsObject

type TechnologyMap = Map[String, Technology]

object Technology {
  def load: IO[TechnologyMap] = IO
    .blocking(
      parser.decode[TechnologyMap](
        scala.io.Source
          .fromResource("patterns.json")
          .iter
          .foldLeft(java.lang.StringBuilder())(_.append(_))
          .toString
      )
    )
    .flatMap(IO.fromEither)
}

type ObjectPattern = Map[String, Set[Regex]]

final case class TechnologyPatterns(
    cookies: ObjectPattern = Map.empty,
    // dom: Set[String] = Set.empty,
    headers: ObjectPattern = Map.empty,
    url: Set[Regex] = Set.empty,
    meta: ObjectPattern = Map.empty,
    scriptSrc: Set[Regex] = Set.empty
) derives Codec.AsObject

object TechnologyPatterns {
  val empty: TechnologyPatterns = TechnologyPatterns()
}

enum Pricing {
  case Low, Mid, High
  case Freemium, Onetime, Recurring, PriceOnAsking, PayAsYouGo
}

object Pricing {
  given Codec[Pricing] = ConfiguredEnumCodec.derive()
  val mapping: Map[String, Pricing] =
    Pricing.values.map(p => p.toString -> p).toMap
}

private given Decoder[Regex] = Decoder.decodeString.map(_.r)
private given Encoder[Regex] = Encoder.encodeString.contramap(_.regex)
