package io.aibees.knowledgebase

import cats.Show
import cats.syntax.all.*
import scala.concurrent.duration.*
import cats.kernel.Order
import cats.kernel.Monoid

final case class Statistics(
    total: Long = 0,
    ok: Long = 0,
    failed: Long = 0,
    timedout: Long = 0,
    byStatus: Map[Int, Long] = Map.empty,
    totalTime: FiniteDuration = Duration.Zero
) {
  private final def addStatus(value: Int) = byStatus.updatedWith(value) {
    case None        => Some(1L)
    case Some(value) => Some(value + 1L)
  }
  private def update(
      result: FetchResult,
      ok: Long = 0,
      failed: Long = 0,
      timedout: Long = 0,
      status: Int = -1
  ): Statistics = copy(
    total = total + 1,
    totalTime = totalTime + result.time,
    ok = ok,
    failed = failed,
    timedout = timedout,
    byStatus = if status > 0 then addStatus(status) else byStatus
  )

  final def add(result: FetchResult): Statistics =
    result.result match {
      case Right(value) => update(result, ok = ok + 1, status = value.status)
      case Left(FetchError.Timeout) => update(result, timedout = timedout + 1)
      case Left(FetchError.Failed)  => update(result, failed = failed + 1)
      case Left(FetchError.BadStatus(status, _)) =>
        update(result, status = status)
    }
}

object Statistics {
  given Show[Statistics] = Show.show(st => s"""Statistics:
=======================
OK: ${st.ok}
Failed: ${st.failed}
Timedout: ${st.timedout}
${st.byStatus.map((k, v) => s"$k: $v").mkString("\n")}
Total: ${st.total}
Total fetch time: ${st.totalTime}
=======================
""")

  import scala.concurrent.duration.*
  import cats.effect.kernel.RefSink
  import cats.effect.IO
  import cats.syntax.all.*
  import fs2.Pipe
  import fs2.Stream.*
  import io.odin.Logger

  def calculate(
      printInterval: FiniteDuration = 10.seconds
  )(using logger: Logger[IO]): Pipe[IO, FetchResult, FetchResult] =
    in => {
      eval(IO.ref(Statistics())).flatMap { stats =>
        val print = stats.get.flatMap(logger.info(_))

        in.through(metrics(stats))
          .onFinalize(print)
          .concurrently(awakeEvery[IO](printInterval).foreach(_ => print))
      }
    }

  def calculateNested(
      printInterval: FiniteDuration = 10.seconds
  )(using logger: Logger[IO]): Pipe[IO, WebsiteData, WebsiteData] =
    in => {
      eval(IO.ref(Statistics())).flatMap { stats =>
        val print = stats.get.flatMap(logger.info(_))

        in.through(metricsNested(stats))
          .onFinalize(print)
          .concurrently(awakeEvery[IO](printInterval).foreach(_ => print))
      }
    }
  private def metricsNested(
      stats: RefSink[IO, Statistics]
  )(using logger: Logger[IO]): Pipe[IO, WebsiteData, WebsiteData] =
    _.zipWithScan(Statistics())((s, wd) =>
      wd.children.foldLeft(s.add(wd.home))(_ add _)
    )
      .evalTap((_, s) => stats.set(s))
      .map(_._1)

  private def metrics(
      stats: RefSink[IO, Statistics]
  )(using logger: Logger[IO]): Pipe[IO, FetchResult, FetchResult] =
    _.zipWithScan(Statistics())(_.add(_))
      .evalTap((_, s) => stats.set(s))
      .map(_._1)

}

final class Distribution[T: Order] private (
    buckets: Vector[T],
    counts: Vector[Long]
) {
  def add(t: T) = {
    val idx = buckets.indexWhere(_ >= t) match {
      case -1 => counts.size - 1
      case n  => n
    }

    new Distribution(
      counts = counts.updated(idx, counts(idx) + 1),
      buckets = buckets
    )
  }

  override def toString(): String =
    buckets.zip(counts).map((t, c) => s"$t : $c").mkString("\n")
}

object Distribution {
  def apply[T: Order: Monoid](start: T, step: T, count: Int) = new Distribution(
    buckets = Vector.fill(count - 1)(step).scan(start)(_ combine _),
    counts = Vector.fill(count)(0L)
  )
}
