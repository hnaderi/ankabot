package dev.hnaderi.ankabot

import cats.syntax.all.*
import io.circe.Codec

import java.net.URI

final case class WebsiteExtractedData(
    domain: URI,
    extracted: ExtractedData = ExtractedData.empty,
    pages: Set[URI] = Set.empty
) derives Codec.AsObject
