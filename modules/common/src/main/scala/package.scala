package dev.hnaderi.ankabot

import cats.effect.IO
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream.*
import io.odin.Logger
import scodec.bits.ByteVector

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object Helpers {
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

  def decodeSources(using logger: Logger[IO]): Pipe[IO, String, URI] =
    _.through(fs2.text.lines)
      .filterNot(_.isBlank())
      .evalTap(logger.debug(_))
      .through(toUri)

  private val md5 = "MD5"
  def md5Hex(str: String): String = {
    val digest = MessageDigest.getInstance(md5);
    digest.update(str.getBytes(StandardCharsets.UTF_8))

    val out = digest.digest()

    ByteVector.view(out).toBase58
  }
}

extension (s: String) {
  inline def md5Hash: String = Helpers.md5Hex(s)
}
