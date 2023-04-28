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
import scala.concurrent.duration.*

def Scraper(
    sources: Stream[IO, URI],
    timeout: FiniteDuration,
    maxParallel: Int,
    result: Path
)(using logger: Logger[IO]): Stream[IO, Unit] = for {
  // fetcher <- eval(JClient()).map(Fetcher(_))
  fetcher <- resource(EClient(timeout)).map(Fetcher(_, timeout))
  _ <- sources
    .parEvalMap(maxParallel)(fetcher(_))
    .through(Statistics.calculate())
    .evalTap {
      case FetchResult(uri, Right(data)) =>
        IO.fromEither(JsoupWebPage(data)).flatMap { wp =>
          logger.info(wp.links.filter(interesting).asJson)
        }
      case _ => IO.unit
    }
    .through(Storage.persist(result))
} yield ()

private def interesting(link: Link): Boolean = {
  val text = link.text.toLowerCase
  val url = link.value.toString

  text.contains("contact") || text.contains("about")
}
