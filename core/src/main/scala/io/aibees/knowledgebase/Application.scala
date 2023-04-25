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
import org.http4s.jdkhttpclient.JdkHttpClient

import java.net.URI

def Application(source: Path, result: Path)(using
    Logger[IO]
): Stream[IO, Unit] = for {
  fetcher <- eval(JClient()).map(JdkHttpClient[IO](_)).map(Fetcher(_))
  sources = Storage.sources(source)
  _ <- sources
    .through(process(fetcher))
    .through(Storage.write(result))
} yield ()

def process(fetch: Fetcher): Pipe[IO, Uri, PersistedResult] =
  _.parEvalMap(10)(fetch(_))
    .through(Metrics)
    .evalMap(
      _.traverse(traffik =>
        Extractor(traffik.body).map(PersistedData(traffik, _))
      )
    )

def Metrics: Pipe[IO, FetchResult, FetchResult] = in => {
  def go(
      in: Stream[IO, FetchResult],
      stats: Statistics = Statistics()
  ): Pull[cats.effect.IO, FetchResult, Unit] =
    in.pull.uncons1.flatMap {
      case None             => Pull.eval(IO.println(stats))
      case Some(data, next) => go(next, stats.add(data))
    }

  go(in).stream
}
