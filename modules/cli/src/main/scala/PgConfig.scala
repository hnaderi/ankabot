package dev.hnaderi.ankabot
package db

import cats.syntax.all.*

final case class PgConfig(
    host: String = "localhost",
    port: Int = 5432,
    username: String = "postgres",
    password: Option[String] = Some("postgres"),
    database: String = "postgres",
    poolSize: Int = 10
)

object PgConfig {
  def opts = (
    opt(
      name = "pg-host",
      env = "POSTGRES_HOST",
      help = "postgres host",
      default = "localhost"
    ),
    opt(
      name = "pg-port",
      env = "POSTGRES_PORT",
      help = "postgres port",
      default = 5432
    ),
    opt(
      name = "pg-username",
      env = "POSTGRES_USERNAME",
      help = "postgres username",
      default = "postgres"
    ),
    opt(
      name = "pg-password",
      env = "POSTGRES_PASSWORD",
      help = "postgres password",
      default = "postgres"
    ).orNone,
    opt(
      name = "pg-database",
      env = "POSTGRES_DATABASE",
      help = "postgres database",
      default = "postgres"
    ),
    opt(
      name = "pg-pool-size",
      env = "POSTGRES_POOL_SIZE",
      help = "postgres connection pool size",
      default = 10
    )
  ).mapN(PgConfig(_, _, _, _, _, _))

}
