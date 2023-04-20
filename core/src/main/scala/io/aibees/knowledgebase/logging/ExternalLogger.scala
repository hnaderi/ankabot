package org.slf4j.impl

import cats.effect.IO
import cats.effect.Sync
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import io.odin.*
import io.odin.slf4j.OdinLoggerBinder

//effect type should be specified inbefore
//log line will be recorded right after the call with no suspension
class ExternalLogger extends OdinLoggerBinder[IO] {

  implicit val F: Sync[IO] = IO.asyncForIO
  implicit val dispatcher: Dispatcher[IO] =
    Dispatcher.parallel[IO].allocated.unsafeRunSync()._1

  val loggers: PartialFunction[String, Logger[IO]] = {
    case pkg if pkg.startsWith("org.http4s") =>
      consoleLogger[IO](minLevel = Level.Info) // disable noisy external logs
    case _ => // if wildcard case isn't provided, default logger is no-op
      consoleLogger[IO]()
  }
}
