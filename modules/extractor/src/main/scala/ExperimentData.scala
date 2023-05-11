package io.aibees.knowledgebase

import java.net.URI
import io.circe.Codec

final case class ExperimentData(
    source: URI,
    contacts: Set[Contact] = Set.empty,
    technologies: Set[String] = Set.empty,
    children: Set[URI] = Set.empty
) derives Codec.AsObject
