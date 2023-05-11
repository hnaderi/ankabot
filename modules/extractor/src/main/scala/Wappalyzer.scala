package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.io.file.Files
import fs2.io.file.Path
import io.circe.Decoder.OptionDecoder
import io.circe.*
import io.circe.derivation.ConfiguredEnumCodec
import io.circe.parser.decode

import java.net.URI
import scala.util.matching.Regex

object Wappalyzer {
  private given Decoder[Pricing] = Decoder.decodeString.emap {
    case "low"       => Pricing.Low.asRight
    case "mid"       => Pricing.Mid.asRight
    case "high"      => Pricing.High.asRight
    case "freemium"  => Pricing.Freemium.asRight
    case "onetime"   => Pricing.Onetime.asRight
    case "recurring" => Pricing.Recurring.asRight
    case "poa"       => Pricing.PriceOnAsking.asRight
    case "payg"      => Pricing.PayAsYouGo.asRight
    case other       => Left(s"Unknown pricing value: `$other`")
  }
  private def valOrArray[T: Decoder]: Decoder[Set[T]] =
    OptionDecoder(using Decoder.decodeSet[T].or(Decoder[T].map(Set(_))))
      .map(_.getOrElse(Set.empty))
  private val regexMap: Decoder[ObjectPattern] =
    OptionDecoder(using
      Decoder.decodeMap(using
        KeyDecoder[String],
        Decoder[Regex].map(Set(_)).orElse(Decoder[Set[Regex]])
      )
    ).map(_.getOrElse(Map.empty))

  private given patterns: Decoder[TechnologyPatterns] = (c: HCursor) =>
    for {
      cookies <- c.downField("cookies").as(using regexMap)
      // dom <- c.downField("dom").as(valOrArray[Set[String]])
      headers <- c.downField("headers").as(using regexMap)
      url <- c.downField("url").as(valOrArray[Regex])
      meta <- c.downField("meta").as(using regexMap)
      scriptSrc <- c.downField("scriptSrc").as(valOrArray[Regex])
    } yield TechnologyPatterns(
      cookies = cookies,
      headers = headers,
      url = url,
      meta = meta,
      scriptSrc = scriptSrc
    )

  private given Decoder[Technology] = (c: HCursor) =>
    for {
      description <- c.downField("").as[Option[String]]
      icon <- c.downField("icon").as[Option[String]]
      website <- c.downField("website").as[Option[String]]
      saas <- c.downField("saas").as[Option[Boolean]]
      oss <- c.downField("oss").as[Option[Boolean]]
      cpe <- c.downField("cpe").as[Option[String]]
      pricing <- c.downField("pricing").as(using valOrArray[Pricing])
      implies <- c.downField("implies").as(using valOrArray[String])
      requires <- c.downField("requires").as(using valOrArray[String])
      requiresCategory <- c
        .downField("requiresCategory")
        .as(using valOrArray[Int])
      excludes <- c.downField("excludes").as(using valOrArray[String])
      tech <- patterns(c)
    } yield Technology(
      description = description,
      icon = icon,
      website = website,
      saas = saas,
      oss = oss,
      cpe = cpe,
      pricing = pricing,
      implies = implies,
      requires = requires,
      requiresCategory = requiresCategory,
      excludes = excludes,
      patterns = tech
    )

  def file(path: Path): IO[Map[String, Technology]] = Files[IO]
    .readUtf8(path)
    .compile
    .string
    .flatMap(s => IO.fromEither(decode[Map[String, Technology]](s)))

  def apply(path: Path): IO[Map[String, Technology]] = Files[IO]
    .list(path, "*.json")
    .evalMap(file(_))
    .compile
    .fold(Map.empty)(_ ++ _)
}
