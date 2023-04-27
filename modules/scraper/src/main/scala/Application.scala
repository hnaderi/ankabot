package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.Stream.*
import fs2.io.file.Path
import io.odin.Logger
import java.net.URI
import io.circe.syntax.*

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

def process(
    fetch: Fetcher
)(using logger: Logger[IO]): Pipe[IO, URI, FetchResult] =
  _.parEvalMap(10)(fetch(_))
    .through(Statistics.calculate())
    .evalTap {
      case FetchResult(uri, Right(data)) =>
        IO.fromEither(JsoupWebPage(data)).flatMap { wp =>
          logger.info(wp.links.filter(interesting).asJson)
        }
      case _ => IO.unit
    }

private def interesting(link: Link): Boolean = {
  val text = link.text.toLowerCase
  val url = link.value.toString

  text.contains("contact") || text.contains("about")
}
