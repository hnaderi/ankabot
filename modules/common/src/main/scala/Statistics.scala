package io.aibees.knowledgebase

import cats.Show
import cats.syntax.all.*

import scala.concurrent.duration.*

final case class Statistics(
    total: Long = 0,
    ok: Long = 0,
    failed: Long = 0,
    timedout: Long = 0,
    byStatus: Map[Int, Long] = Map.empty,
    totalTime: FiniteDuration = Duration.Zero,
    timeDist: Distribution[FiniteDuration] =
      Distribution(100.millis, 500.millis, 10),
    childPageDist: Distribution[Long] =
      Distribution.buckets(0, 1, 5, 10, 50, 100)
) {
  private final def addStatus(value: Int) = byStatus.updatedWith(value) {
    case None        => Some(1L)
    case Some(value) => Some(value + 1L)
  }
  private def update(
      result: FetchResult,
      ok: Long = ok,
      failed: Long = failed,
      timedout: Long = timedout,
      status: Int = -1
  ): Statistics = copy(
    total = total + 1,
    totalTime = totalTime + result.time,
    timeDist = timeDist.add(result.time),
    ok = ok,
    failed = failed,
    timedout = timedout,
    byStatus = if status > 0 then addStatus(status) else byStatus
  )

  private def add(result: FetchResult): Statistics =
    result.result match {
      case Right(value) => update(result, ok = ok + 1, status = value.status)
      case Left(FetchError.Timeout) => update(result, timedout = timedout + 1)
      case Left(FetchError.Failed)  => update(result, failed = failed + 1)
      case Left(FetchError.BadStatus(status, _)) =>
        update(result, status = status)
    }

  final def add(
      result: WebsiteData,
      actualChildCount: Option[Int] = None
  ): Statistics =
    result.children
      .foldLeft(add(result.home))(_ add _)
      .copy(childPageDist =
        childPageDist.add(actualChildCount.getOrElse(result.children.size))
      )

  final def add(result: FetchResult, childCount: Long): Statistics =
    add(result).copy(childPageDist = childPageDist.add(childCount))
}

object Statistics {
  given Show[Statistics] = Show.show { st =>
    val avg: String =
      if (st.total == 0)
      then "N/A"
      else (st.totalTime / st.total).toString

    s"""Statistics:
====== Requests =======
Overall:
  OK: ${st.ok}
  Failed: ${st.failed}
  Timedout: ${st.timedout}
Statuses:
${st.byStatus.map((k, v) => s"  $k: $v").mkString("\n")}
Total: ${st.total}
Fetch time:
  total: ${st.totalTime}
  avg:   $avg
=======================
======== Times ========
${st.timeDist}
=======================
====== Children =======
${st.childPageDist}
=======================
"""
  }
}
