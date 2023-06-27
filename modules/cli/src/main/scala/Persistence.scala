package dev.hnaderi.ankabot

import cats.effect.IO
import cats.effect.kernel.Resource
import dev.hnaderi.ankabot.worker.*
import fs2.Chunk
import fs2.Stream
import io.odin.Logger
import skunk.Session

object Persistence {
  def apply(
      dbCon: Resource[IO, Resource[IO, Session[IO]]],
      s3Config: S3Config
  )(using Logger[IO]): Stream[IO, (Persistence, Stream[IO, Nothing])] = for {
    db <- Stream.resource(dbCon.map(DBPersistence(_)))

    (s3, upload) <- Stream.resource(
      s3Config match {
        case S3Config.Enabled(config) =>
          S3Persistence(config).map(p =>
            (p.write(_: Chunk[Worker.Result]).compile.drain, p.upload)
          )
        case S3Config.Disabled =>
          Resource.pure(((_: Chunk[Worker.Result]) => IO.unit, Stream.empty))
      }
    )

  } yield (data => db(data) &> s3(data), upload)
}
