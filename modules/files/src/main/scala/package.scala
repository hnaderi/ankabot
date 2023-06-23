package dev.hnaderi.ankabot.storage

import eu.timepit.refined.types.string.NonEmptyString
import fs2.aws.s3.models.Models.PartSizeMB
import fs2.io.file.Path

import java.util.UUID

type Batch = List[Path]
final case class BatchFile(
    file: Path,
    content: Batch
)

type PartFile = Path

extension (u: UUID) {
  def toNonEmptyString: NonEmptyString = NonEmptyString.unsafeFrom(u.toString)
}

extension (ns: NonEmptyString) {
  def append(s: String): NonEmptyString =
    NonEmptyString.unsafeFrom(ns.value.concat(s))
}

import scala.quoted.*
object NS {
  private def check(expr: Expr[String])(using Quotes): Expr[String] =
    expr.value match {
      case Some(value) if !value.isEmpty => Expr(value)
      case Some(value) => throw IllegalArgumentException("Is empty!")
      case None        => throw IllegalArgumentException("Not an inline value")
    }

  private inline def app(inline str: String): String = ${ check('str) }

  inline def apply(inline str: String): NonEmptyString =
    NonEmptyString.unsafeFrom(app(str))
}

object PartSize {
  private def check(expr: Expr[Int])(using Quotes): Expr[Int] =
    expr.value match {
      case Some(value) if value >= 5 => Expr(value)
      case Some(value) =>
        throw IllegalArgumentException(
          s"Must be greater or equal to 5, but is $value"
        )
      case None => throw IllegalArgumentException("Not an inline value")
    }
  private inline def app(inline value: Int): Int = ${ check('value) }
  inline def apply(inline value: Int): PartSizeMB =
    PartSizeMB.unsafeFrom(app(value))
}

extension (i: Int) {
  inline def KB = i * 1024
  inline def MB = i.KB * 1024
}
