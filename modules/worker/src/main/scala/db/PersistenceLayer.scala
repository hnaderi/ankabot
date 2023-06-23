package dev.hnaderi.ankabot
package db

import cats.effect.Sync
import cats.implicits.*
import io.odin.Logger
import skunk.Session
import skunk.implicits.*

import scala.io.Codec

object PersistenceLayer {
  private def loadSQLFrom[F[_]](name: String)(using F: Sync[F]): F[String] =
    F.delay(
      scala.io.Source
        .fromResource(s"sql/$name.sql")(Codec.UTF8)
        .getLines()
        .mkString("\n")
    )

  private def runMigration[F[_]: Sync](
      name: String,
      session: Session[F],
      logger: Logger[F]
  ): F[Unit] =
    for {
      init <- loadSQLFrom(name)
      q = sql"#$init".command
      _ <- logger.debug(s"running migration: $name")
      _ <- session.execute(q)
    } yield ()

  def migrate[F[_]: Sync: Logger](session: Session[F], module: String)(
      migrations: String*
  ): F[Unit] = migrate(session)(migrations.map(s => s"$module-$s"): _*)

  private def migrate[F[_]: Sync](session: Session[F])(migrations: String*)(
      using logger: Logger[F]
  ): F[Unit] =
    migrations.traverse(runMigration(_, session, logger)).void
}
