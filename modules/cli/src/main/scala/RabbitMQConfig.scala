package io.aibees.knowledgebase

import cats.syntax.all.*
import com.comcast.ip4s.*
import com.monovore.decline.Argument
import com.monovore.decline.Opts

final case class RabbitMQConfig(
    host: Host = host"localhost",
    port: Port = port"5672",
    username: String = "guest",
    password: String = "guest"
)

object RabbitMQConfig {
  def opts = (
    Opts
      .option[Host]("rmq-host", "rabbit mq host", "R")
      .withDefault(host"localhost"),
    Opts
      .option[Port]("rmq-port", "rabbit mq port", "P")
      .withDefault(port"5672"),
    Opts
      .option[String]("rmq-username", "rabbit mq username", "u")
      .withDefault("guest"),
    Opts
      .option[String]("rmq-password", "rabbit mq password", "p")
      .withDefault("guest"),
  ).mapN(RabbitMQConfig(_, _, _, _))
}
