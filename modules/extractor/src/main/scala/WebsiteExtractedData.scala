package dev.hnaderi.ankabot

import cats.syntax.all.*
import io.circe.Codec

import java.net.URI

final case class WebsiteExtractedData(
    domain: URI,
    extracted: ExtractedData = ExtractedData.empty,
    pages: Set[URI] = Set.empty,
    info: WebsiteInfoData = WebsiteInfoData.empty
) derives Codec.AsObject

final case class WebsiteInfoData(
    icons: Set[String] = Set.empty,
    logos: Set[String] = Set.empty,
    title: Option[String] = None,
    name: Option[String] = None,
    description: Option[String] = None,
    image: Option[URI] = None,
    locale: Option[String] = None
) derives Codec.AsObject

object WebsiteInfoData {
  val empty: WebsiteInfoData = WebsiteInfoData()
}
