package io.aibees.knowledgebase

import cats.effect.IO
import cats.implicits.*
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

def Application()(using Logger[IO]): Stream[IO, Unit] = for {
  fetcher <- eval(JClient()).map(JdkHttpClient[IO](_)).map(Fetcher(_))
  sources = Sources(
    Path("/storage/projects/ai-bees/knowledge-base/seed/seeds.txt")
  )
  // sources = Stream(uri"https://www.oryxalign.com/")
  _ <- sources.through(process(fetcher))
} yield ()

def process(fetch: Fetcher)(using logger: Logger[IO]): Pipe[IO, Uri, Nothing] =
  _.parEvalMap(10)(fetch(_).attempt).foreach {
    case Right(traffik) =>
      for {
        _ <- logger.info("Traffik")
        _ <- IO.println(traffik.asJson.spaces2)

        data <- Extractor(traffik.body)
        _ <- logger.info("Scraped")
        _ <- IO.println(data.asJson.spaces2)
      } yield ()
    case Left(err) => IO.println("Skipping...")
  }

def Sources(path: Path)(using logger: Logger[IO]): Stream[IO, Uri] = Files[IO]
  .readAll(path)
  .through(fs2.text.utf8.decode)
  .through(fs2.text.lines)
  .evalTap(logger.debug(_))
  .map(Uri.fromString)
  .flatMap(Stream.fromEither(_))
