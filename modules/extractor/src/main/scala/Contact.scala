package io.aibees.knowledgebase

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
    Telegram,
    Instagram,
    Twitter,
    Facebook,
    Youtube,
}

object SocialNetwork {
  given Codec[SocialNetwork] = ConfiguredEnumCodec.derive()
}
