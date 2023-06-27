package dev.hnaderi.ankabot.worker

import cats.effect.IO
import cats.effect.kernel.Resource
import io.odin.Logger
import skunk.Session

object Persistence {
  def apply(
      pool: Resource[IO, Session[IO]],
      s3Config: Option[S3Persistence.Config] = None
  )(using Logger[IO]): Resource[IO, Persistence] = {
    val db = DBPersistence(pool)
    val s3P: Resource[IO, Persistence] = s3Config.map(S3Persistence(_)) match {
      case Some(s3) => s3.map[Persistence](p => p.write(_).compile.drain)
      case None     => Resource.pure[IO, Persistence](_ => IO.unit)
    }

    s3P.map(s3 => data => db(data) *> s3(data))
  }
}
