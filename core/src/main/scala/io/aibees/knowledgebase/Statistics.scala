package io.aibees.knowledgebase

import cats.Show
import cats.syntax.all.*

final case class Statistics(
    total: Long = 0,
    ok: Long = 0,
    failed: Long = 0,
    timedout: Long = 0,
    byStatus: Map[Int, Long] = Map.empty
) {
  private final def addStatus(value: Int) = byStatus.updatedWith(value) {
    case None        => Some(1L)
    case Some(value) => Some(value + 1L)
  }

  final def add(result: ScrapeResult[RawScrapedData]): Statistics =
    result.result match {
      case Right(value) =>
        copy(total = total + 1, ok = ok + 1, byStatus = addStatus(value.status))
      case Left(ScrapeError.Timeout) =>
        copy(total = total + 1, timedout = timedout + 1)
      case Left(ScrapeError.Failed) =>
        copy(total = total + 1, failed = failed + 1)
      case Left(ScrapeError.BadStatus(status, _)) =>
        copy(total = total + 1, byStatus = addStatus(status))
    }
}

object Statistics {
  given Show[Statistics] = Show.show(st => s"""OK: ${st.ok}
Failed: ${st.failed}
Timedout: ${st.timedout}
${st.byStatus.map((k, v) => s"$k: $v").mkString("\n")}

Total: ${st.total}
""")
}
