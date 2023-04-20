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
import org.http4s.jdkhttpclient.JdkHttpClient

def Application()(using Logger[IO]): Stream[IO, Unit] = for {
  scraper <- eval(JClient()).map(JdkHttpClient[IO](_)).map(Scraper(_))
  _ <- Sources(
    Path("/storage/projects/ai-bees/knowledge-base/seeds.txt")
  ).through(process(scraper))
} yield ()

def process(scraper: Scraper): Pipe[IO, Uri, Nothing] =
  _.parEvalMap(10)(scraper.get(_).attempt).foreach {
    case Right(traffik) =>
      for {
        _ <- Extractor(traffik.body)
        _ <- IO.println("Headers")
        _ <- traffik.headers.headers.traverse(IO.println)
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
