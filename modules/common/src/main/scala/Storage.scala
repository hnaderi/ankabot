package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import io.circe.Json
import io.circe.syntax.*
import io.odin.Logger

import Stream.*

import java.net.URI

object Storage {

  def load(path: Path): Stream[IO, PersistedResult] = Files[IO]
    .readAll(path)
    .through(fs2.text.utf8.decode)
    .through(fs2.text.lines)
    .filterNot(_.isBlank())
    .map(io.circe.parser.decode[PersistedResult](_))
    .flatMap(fromEither(_))

  def sources(path: Path)(using logger: Logger[IO]): Stream[IO, URI] = Files[IO]
    .readAll(path)
    .through(fs2.text.utf8.decode)
    .through(fs2.text.lines)
    .filterNot(_.isBlank())
    .evalTap(logger.debug(_))
    .through(toUri)

  def write(path: Path): Pipe[IO, PersistedResult, Nothing] =
    _.map(_.asJson.noSpaces)
      .intersperse("\n")
      .through(fs2.text.utf8.encode)
      .through(Files[IO].writeAll(path))

  private def toUri(using logger: Logger[IO]): Pipe[IO, String, URI] =
    _.flatMap { s =>
      val uri =
        if (s.startsWith("http"))
        then Either.catchNonFatal(URI(s))
        else Either.catchNonFatal(URI(s"http://$s"))

      uri match {
        case Right(value) => emit(value)
        case Left(err)    => exec(logger.warn("Invalid source!", err))
      }
    }
}
