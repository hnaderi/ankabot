package dev.hnaderi.ankabot

import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.all.*
import fs2.io.file.Files
import fs2.io.file.Path
import io.circe.syntax.*

import java.nio.file.{Files => JFiles}
import java.nio.file.{Path => JPath}

object Main extends IOApp.Simple {
  override def run: IO[Unit] = Wappalyzer(
    Path("/storage/projects/ai-bees/knowledge-base/patterns")
  )
    .map(_.asJson.noSpaces)
    .flatMap(out =>
      IO.blocking(
        JFiles.writeString(JPath.of("src/main/resources/patterns.json"), out)
      )
    )

}
