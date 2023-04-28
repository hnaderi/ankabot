package io.aibees.knowledgebase

import fs2.io.file.Path
import scala.concurrent.duration.*
import com.monovore.decline.Command
import com.monovore.decline.Opts
import cats.syntax.all.*

final case class RunOptions(
    output: Path,
    input: Option[Path] = None,
    maxParallel: Int = 10
)

object RunOptions {
  val cmd = Command[RunOptions]("extract", "", true) {
    (
      Opts.option[Path]("output", "Output file", "o"),
      Opts.option[Path]("input", "Input file", "i").orNone,
      Opts.option[Int]("max-parallel", "Max parallel", "n").withDefault(10),
    ).mapN(RunOptions(_, _, _))
  }
}
