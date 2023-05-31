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
    opt[Host](
      name = "rmq-host",
      env = "RABBITMQ_HOST",
      help = "rabbit mq host",
      default = host"localhost"
    ),
    opt(
      name = "rmq-port",
      env = "RABBITMQ_PORT",
      help = "rabbit mq port",
      default = port"5672"
    ),
    opt(
      name = "rmq-username",
      env = "RABBITMQ_USERNAME",
      help = "rabbit mq username",
      default = "guest"
    ),
    opt(
      name = "rmq-password",
      env = "RABBITMQ_PASSWORD",
      help = "rabbit mq password",
      default = "guest"
    )
  ).mapN(RabbitMQConfig(_, _, _, _))
}
