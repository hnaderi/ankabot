package dev.hnaderi.ankabot

import cats.effect.IO
import fs2.Pipe
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import io.circe.Decoder
import io.circe.Encoder
import io.circe.syntax.*
import io.odin.Logger

import java.net.URI

import Stream.*

object Storage {

  def load[T: Decoder](path: Path): Stream[IO, T] =
    read(path).through(decodeResult)

  val stdin: Stream[IO, Byte] = fs2.io.stdin[IO](4096)

  def stdinResults[T: Decoder]: Stream[IO, T] = stdin.through(decodeResult)

  private def decodeResult[T: Decoder]: Pipe[IO, Byte, T] =
    _.through(fs2.text.utf8.decode)
      .through(fs2.text.lines)
      .filter(_.size < 1e7)
      .through(io.circe.fs2.stringStreamParser)
      .flatMap(j => fromEither(j.as[T]))

  def sources(path: Path)(using Logger[IO]): Stream[IO, URI] =
    read(path).through(fs2.text.utf8.decode).through(Helpers.decodeSources)

  def stdinSources(using Logger[IO]): Stream[IO, URI] =
    stdin.through(fs2.text.utf8.decode).through(Helpers.decodeSources)

  def persist[T: Encoder](path: Path): Pipe[IO, T, Nothing] =
    _.through(encodeJsonline).through(write(path))

  private def isGzip(path: Path) = path.extName.toLowerCase.endsWith(".gz")

  def encodeJsonline[F[_], T: Encoder]: Pipe[F, T, Byte] =
    _.map(_.asJson.noSpaces).intersperse("\n").through(fs2.text.utf8.encode)

  def decodeJsonline[T: Decoder]: Pipe[IO, Byte, T] =
    _.through(decodeResult)

  def writeString(path: Path): Pipe[IO, String, Nothing] =
    _.through(fs2.text.utf8.encode).through(write(path))

  def write(path: Path): Pipe[IO, Byte, Nothing] = in => {
    val out =
      if isGzip(path)
      then in.through(fs2.compression.Compression[IO].gzip())
      else in
    out.through(Files[IO].writeAll(path))
  }

  def read(path: Path): Stream[IO, Byte] = {
    val in = Files[IO].readAll(path)
    if isGzip(path)
    then in.through(fs2.compression.Compression[IO].gunzip()).flatMap(_.content)
    else in
  }
}
