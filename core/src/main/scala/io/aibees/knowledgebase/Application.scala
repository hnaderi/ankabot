package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.Stream.*
import fs2.io.file.Files
import fs2.io.file.Path
import io.odin.Logger
import org.http4s.Uri
import org.http4s.syntax.literals.uri
import org.http4s.jdkhttpclient.JdkHttpClient
import io.circe.syntax.*
import io.circe.Json
import java.net.URI

// private val resultPath = Path(
//   "/storage/projects/ai-bees/knowledge-base/result.json.line"
// )
// private val seedsPath = Path(
//   "/storage/projects/ai-bees/knowledge-base/seed/seeds.txt"
// )

def Application(source: Path, result: Path)(using
    Logger[IO]
): Stream[IO, Unit] = for {
  fetcher <- eval(JClient()).map(JdkHttpClient[IO](_)).map(Fetcher(_))
  sources = Sources(source)
  _ <- sources
    .through(process(fetcher))
    .map(_.asJson)
    .through(Result(result))
} yield ()

def process(fetch: Fetcher): Pipe[IO, Uri, ScrapeResult[PersistedResult]] =
  _.parEvalMap(10)(fetch(_))
    .evalMap(
      _.traverse(traffik =>
        Extractor(traffik.body).map(PersistedResult(traffik, _))
      )
    )

def Sources(path: Path)(using logger: Logger[IO]): Stream[IO, Uri] = Files[IO]
  .readAll(path)
  .through(fs2.text.utf8.decode)
  .through(fs2.text.lines)
  .evalTap(logger.debug(_))
  .through(toUri)

def Result(path: Path): Pipe[IO, Json, Nothing] = _.map(_.noSpaces)
  .intersperse("\n")
  .through(fs2.text.utf8.encode)
  .through(Files[IO].writeAll(path))

private def toUri: Pipe[IO, String, Uri] = _.flatMap { s =>
  val uri =
    if (s.startsWith("http"))
    then Uri.fromString(s)
    else Uri.fromString(s"http://$s")

  uri match {
    case Right(value) => emit(value)
    case Left(err)    => raiseError(err)
  }
}
