package io.aibees.knowledgebase

import cats.syntax.all.*
import com.comcast.ip4s.*
import com.monovore.decline.Argument
import fs2.io.file.Path

import java.nio.file.{Path as JPath}

private given Argument[Host] = Argument.from("hostname")(s =>
  Host.fromString(s).toValidNel(s"Invalid hostname $s")
)
private given Argument[Port] = Argument.from("port number")(s =>
  Port.fromString(s).toValidNel(s"Invalid port number $s")
)

private given Argument[Path] = Argument[JPath].map(Path.fromNioPath)
