package dev.hnaderi.ankabot

import cats.syntax.all.*
import com.comcast.ip4s.*
import com.monovore.decline.Argument
import com.monovore.decline.Opts
import fs2.io.file.Path

import java.nio.file.{Path as JPath}

private def opt[T: Argument](
    name: String,
    env: String,
    help: String,
    default: T
): Opts[T] = opt(
  name = name,
  env = env,
  help = help
).withDefault(default)

private def opt[T: Argument](
    name: String,
    env: String,
    help: String
): Opts[T] = Opts
  .option[T](name, help)
  .orElse(Opts.env(env, help))

private given Argument[Host] = Argument.from("hostname")(s =>
  Host.fromString(s).toValidNel(s"Invalid hostname $s")
)
private given Argument[Port] = Argument.from("port number")(s =>
  Port.fromString(s).toValidNel(s"Invalid port number $s")
)

private given Argument[Path] = Argument[JPath].map(Path.fromNioPath)
