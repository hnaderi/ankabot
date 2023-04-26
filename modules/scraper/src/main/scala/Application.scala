package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Pipe
import fs2.Pull
import fs2.Stream
import fs2.Stream.*
import fs2.io.file.Path
import io.circe.Json
import io.circe.syntax.*
import io.odin.Logger
import org.http4s.Uri
import scala.concurrent.duration.*

import java.net.URI
import cats.effect.kernel.RefSink

def Application(source: Path, result: Path)(using
    Logger[IO]
): Stream[IO, Unit] = for {
  // fetcher <- eval(JClient()).map(Fetcher(_))
  fetcher <- resource(EClient()).map(Fetcher(_))
  sources = Storage.sources(source)
  _ <- sources
    .through(process(fetcher))
    .through(Storage.write(result))
} yield ()

def process(fetch: Fetcher)(using Logger[IO]): Pipe[IO, Uri, PersistedResult] =
  _.parEvalMap(10)(fetch(_))
    .through(Metrics)
    .evalMap(
      _.traverse(traffik =>
        IO.fromEither(JsoupWebPage(traffik.body).map(PersistedData(traffik, _)))
      )
    )

def Metrics(using logger: Logger[IO]): Pipe[IO, FetchResult, FetchResult] =
  in => {
    eval(IO.ref(Statistics())).flatMap { stats =>
      val print = stats.get.flatMap(logger.info(_))

      in.through(MetricsWriter(stats))
        .onFinalize(print)
        .concurrently(awakeEvery[IO](10.seconds).foreach(_ => print))
    }
  }

private def MetricsWriter(
    stats: RefSink[IO, Statistics]
)(using logger: Logger[IO]): Pipe[IO, FetchResult, FetchResult] =
  _.zipWithScan(Statistics())(_.add(_))
    .evalTap((_, s) => stats.set(s))
    .map(_._1)
