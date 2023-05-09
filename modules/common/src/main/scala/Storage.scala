package io.aibees.knowledgebase

import cats.effect.IO
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*
import io.odin.Logger

import java.net.URI

import Stream.*

object Storage {

  def load[T: Decoder](path: Path): Stream[IO, T] =
    read(path).through(decodeResult)

  private val stdin = fs2.io.stdin[IO](4096).through(fs2.text.utf8.decode)

  def stdinResults[T: Decoder]: Stream[IO, T] = stdin.through(decodeResult)

  private def decodeResult[T: Decoder]: Pipe[IO, String, T] =
    _.through(fs2.text.lines)
      .filterNot(_.isBlank())
      .map(io.circe.parser.decode[T](_))
      .flatMap(fromEither(_))

  def sources(path: Path)(using Logger[IO]): Stream[IO, URI] =
    read(path).through(decodeSources)

  def stdinSources(using Logger[IO]): Stream[IO, URI] =
    stdin.through(decodeSources)

  private def decodeSources(using logger: Logger[IO]): Pipe[IO, String, URI] =
    _.through(fs2.text.lines)
      .filterNot(_.isBlank())
      .evalTap(logger.debug(_))
      .through(toUri)

  def persist[T: Encoder](path: Path): Pipe[IO, T, Nothing] =
    _.map(_.asJson.noSpaces)
      .intersperse("\n")
      .through(write(path))

  private def isGzip(path: Path) = path.extName.toLowerCase.endsWith(".gz")

  def write(path: Path): Pipe[IO, String, Nothing] = in => {
    val text = in.through(fs2.text.utf8.encode)
    val out =
      if isGzip(path)
      then text.through(fs2.compression.Compression[IO].gzip())
      else text
    out.through(Files[IO].writeAll(path))
  }
  def read(path: Path): Stream[IO, String] = {
    val in = Files[IO].readAll(path)
    val text =
      if isGzip(path)
      then
        in.through(fs2.compression.Compression[IO].gunzip()).flatMap(_.content)
      else in
    text.through(fs2.text.utf8.decode)
  }

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
