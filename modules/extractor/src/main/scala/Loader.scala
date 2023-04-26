package io.aibees.knowledgebase

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import fs2.io.file.Path
import io.circe.syntax.*

object Loader extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    if args.size == 1 then
      app(Path(args(0))).compile.drain
        .as(ExitCode.Success)
    else IO.println("Must provide persisted data file!").as(ExitCode.Error)

  private def app(path: Path) = Storage.load(path).evalMap { data =>
    data.result.flatMap(JsoupWebPage(_)) match {
      case Right(page) =>
        val result = Extractors
          .all(page)
          .contacts
          .asJson
          .noSpaces

        IO.println(s"""
Extracted from ${data.source}
${result}
""")
      case Left(value) => IO.unit
    }
  }
}
