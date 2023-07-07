package dev.hnaderi.ankabot
package cli

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all.*
import com.monovore.decline.Opts
import dev.hnaderi.ankabot.storage.NS
import dev.hnaderi.ankabot.storage.ObjectStorageReader
import dev.hnaderi.ankabot.storage.ObjectStorageWriter
import fs2.Stream
import fs2.aws.s3.models.Models.BucketName
import fs2.io.file.Path

import S3Config.given_Argument_BucketName

enum InputPath {
  case Local(files: NonEmptyList[Path])
  case S3(
      config: ObjectStorageWriter.Config,
      bucketName: BucketName = BucketName(NS("ankabot")),
      objPrefix: Option[String] = None
  )
  case StdIn

  def read: Stream[IO, Byte] = this match {
    case StdIn        => Storage.stdin
    case Local(files) => Stream.emits(files.toList).flatMap(Storage.read(_))
    case S3(config, bucketName, objPrefix) =>
      Stream
        .resource(ObjectStorageReader(config, bucketName))
        .flatMap(_.read(objPrefix))
  }
}

object InputPath {
  private val s3 = Opts.flag("s3-enabled", "Is s3 upload enabled?") *> (
    S3Config.s3,
    Opts.argument[BucketName]("bucket name"),
    Opts.argument[String]("object prefix").orNone
  ).mapN(InputPath.S3(_, _, _))

  def opts =
    s3
      .orElse(Opts.arguments[Path]("input").map(InputPath.Local(_)))
      .withDefault(InputPath.StdIn)
}
