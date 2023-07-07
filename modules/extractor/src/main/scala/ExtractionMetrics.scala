package dev.hnaderi.ankabot

import cats.Show
import cats.syntax.all.*

import scala.concurrent.duration.*

final case class ExtractionMetrics(
    contacts: Distribution[Long] = Distribution.buckets(0, 1, 5, 10, 100),
    technologies: Distribution[Long] = Distribution.buckets(0, 1, 5, 10, 100),
    time: Distribution[FiniteDuration] = TimeDistribution.millis(10, 50, 10)
) {
  def add(
      extracted: ExtractedData,
      spentTime: FiniteDuration
  ): ExtractionMetrics = ExtractionMetrics(
    contacts = contacts.add(extracted.contacts.size),
    technologies = technologies.add(extracted.technologies.size),
    time = time.add(spentTime)
  )
}

object ExtractionMetrics {
  given Show[ExtractionMetrics] = Show.show(em => s"""
Statistics:
======== Contacts Home ===========
${em.contacts}
======== Technologies Home =======
${em.technologies}
======== Time Home ===========
${em.time}
=============================
""")
}
