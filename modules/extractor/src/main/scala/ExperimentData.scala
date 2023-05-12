package io.aibees.knowledgebase

import cats.Show
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all.*
import io.circe.Codec
import io.odin.Logger

import java.net.URI
import scala.concurrent.duration.*

final case class ExperimentData(
    source: URI,
    contacts: Set[Contact] = Set.empty,
    technologies: Set[String] = Set.empty,
    children: Set[URI] = Set.empty
) derives Codec.AsObject

final case class ExtractionMetrics(
    contacts: Distribution[Long] = Distribution.buckets(0, 1, 5, 10, 100),
    contactsAll: Distribution[Long] = Distribution.buckets(0, 1, 5, 10, 100),
    technologies: Distribution[Long] = Distribution.buckets(0, 1, 5, 10, 100),
    technologiesAll: Distribution[Long] = Distribution.buckets(0, 1, 5, 10, 100)
) {
  def add(
      homeContacts: Set[Contact],
      allContacts: Set[Contact],
      homeTechnologies: Set[String],
      allTechnologies: Set[String]
  ): ExtractionMetrics = ExtractionMetrics(
    contacts = contacts.add(homeContacts.size),
    contactsAll = contactsAll.add(allContacts.size),
    technologies = technologies.add(homeTechnologies.size),
    technologiesAll = technologiesAll.add(allTechnologies.size)
  )
}

object ExtractionMetrics {
  given Show[ExtractionMetrics] = Show.show(em => s"""
Statistics:
======== Contacts Home ===========
${em.contacts}
======== Technologies Home =======
${em.technologies}
======== Contacts All ===========
${em.contactsAll}
======== Technologies All =======
${em.technologiesAll}
=============================
""")
}

final class ExtractionMetricsCollector private (
    stats: Ref[IO, ExtractionMetrics]
) extends AnyVal {
  def add(
      contactsHome: Set[Contact],
      contactsAll: Set[Contact],
      technologiesHome: Set[String],
      technologiesAll: Set[String]
      // timeAvg:FiniteDuration,
      // timeAll:FiniteDuration,
  ): IO[Unit] =
    stats.update(
      _.add(
        homeContacts = contactsHome,
        allContacts = contactsAll,
        homeTechnologies = technologiesHome,
        allTechnologies = technologiesAll
      )
    )

  def print: IO[String] = stats.get.map(_.show)
  def read: IO[ExtractionMetrics] = stats.get
}

object ExtractionMetricsCollector {
  def apply(
      stats: ExtractionMetrics = ExtractionMetrics()
  ): IO[ExtractionMetricsCollector] =
    IO.ref(stats).map(new ExtractionMetricsCollector(_))

  def printer(
      stats: ExtractionMetrics = ExtractionMetrics(),
      interval: FiniteDuration = 10.seconds
  )(using logger: Logger[IO]): fs2.Stream[IO, ExtractionMetricsCollector] =
    for {
      stats <- fs2.Stream.eval(apply(stats))
      print = stats.read.flatMap(logger.info)
      _ <- fs2.Stream
        .awakeEvery[IO](interval)
        .foreach(_ => print)
        .spawn
        .onFinalize(logger.info("Terminated") >> print)
    } yield stats
}
