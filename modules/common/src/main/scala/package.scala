package dev.hnaderi.ankabot

import cats.effect.IO
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream.*
import io.odin.Logger

import java.net.URI

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
}
