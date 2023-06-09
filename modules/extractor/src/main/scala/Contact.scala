package dev.hnaderi.ankabot

import io.circe.Codec
import io.circe.derivation.ConfiguredEnumCodec

import java.net.URI

enum Contact derives Codec.AsObject {
  case Email(value: String)
  case Phone(number: String)
  case Social(network: SocialNetwork, contact: URI)
}

enum SocialNetwork {
  case Linkedin,
    LinkedinCompany,
    Telegram,
    Instagram,
    Twitter,
    Facebook,
    Youtube,
    Github,
    Gitlab,
    Pinterest,
    Medium,
    Crunchbase,
    Twitch,
    TikTok,
}

object SocialNetwork {
  given Codec[SocialNetwork] = ConfiguredEnumCodec.derive()
  val mapping: Map[String, SocialNetwork] =
    SocialNetwork.values.map(p => p.toString -> p).toMap
}
