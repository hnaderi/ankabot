package io.aibees.knowledgebase

import cats.effect.IO
import io.odin.Logger

object Scraper extends CMDApp(RunOptions.cmd) {

  private given Logger[IO] = io.odin.consoleLogger[IO]()

  override def app(t: RunOptions): fs2.Stream[IO, Unit] = Application(
    t.input.fold(Storage.stdinSources)(Storage.sources),
    t.timeout,
    t.maxParallel,
    t.output
  )
}
