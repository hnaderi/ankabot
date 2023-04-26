package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.Stream.*
import fs2.io.file.Path
import io.odin.Logger
import org.http4s.Uri

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
    .through(Statistics.calculate())
    .evalMap(
      _.traverse(traffik =>
        IO.fromEither(
          JsoupWebPage(traffik.body, traffik.url).map(PersistedData(traffik, _))
        )
      )
    )
