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

  final def add(result: FetchResult): Statistics =
    result.result match {
      case Right(value) =>
        copy(total = total + 1, ok = ok + 1, byStatus = addStatus(value.status))
      case Left(FetchError.Timeout) =>
        copy(total = total + 1, timedout = timedout + 1)
      case Left(FetchError.Failed) =>
        copy(total = total + 1, failed = failed + 1)
      case Left(FetchError.BadStatus(status, _)) =>
        copy(total = total + 1, byStatus = addStatus(status))
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

  private def metrics(
      stats: RefSink[IO, Statistics]
  )(using logger: Logger[IO]): Pipe[IO, FetchResult, FetchResult] =
    _.zipWithScan(Statistics())(_.add(_))
      .evalTap((_, s) => stats.set(s))
      .map(_._1)
}
