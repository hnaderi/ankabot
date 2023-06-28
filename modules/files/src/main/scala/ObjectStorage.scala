package dev.hnaderi.ankabot
package storage

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.syntax.all.*
import eu.timepit.refined.types.string.NonEmptyString
import fs2.Pipe
import fs2.Stream
import fs2.aws.s3.S3
import fs2.aws.s3.models.Models.BucketName
import fs2.aws.s3.models.Models.FileKey
import fs2.aws.s3.models.Models.PartSizeMB
import fs2.io.file.Files
import fs2.io.file.Path
import io.laserdisc.pure.s3.tagless.Interpreter
import io.odin.Logger
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient

import java.net.URI

import Stream.*

object ObjectStorage {
  final case class Config(
      address: URI,
      credentials: S3Credentials,
      pathStyle: Boolean = false,
      region: Option[String] = None
  )

  def build(config: Config): Resource[IO, S3[IO]] = {
    val builder = IO {
      val b = S3AsyncClient
        .builder()
        .credentialsProvider(
          StaticCredentialsProvider.create(
            AwsBasicCredentials.create(
              config.credentials.accessKey,
              config.credentials.secretKey
            )
          )
        )
        .endpointOverride(config.address)
        .region(config.region.fold(Region.US_EAST_1)(Region.of(_)))

      if config.pathStyle
      then b.forcePathStyle(true)
      else b
    }.toResource

    builder
      .flatMap(Interpreter[IO].S3AsyncClientOpResource)
      .map(S3.create)
  }

  def apply(
      s3: S3[IO],
      storage: BatchStorage,
      bucket: BucketName,
      objPrefix: NonEmptyString,
      partSize: PartSizeMB
  )(using logger: Logger[IO]): IO[ObjectStorage] = for {

    toUpload <- Queue.unbounded[IO, Path]
  } yield new ObjectStorage {

    private def toNS(str: String) =
      IO.fromEither(
        NonEmptyString
          .from(str)
          .leftMap(IllegalArgumentException(_))
      )

    override def write: Pipe[IO, Byte, Nothing] = storage.write

    override def read(name: String): Stream[IO, Byte] =
      eval(toNS(name))
        .map(FileKey(_))
        .flatMap(s3.readFile(bucket, _))

    override def upload: Stream[IO, Nothing] = {

      def readConcat(batch: BatchFile): Stream[IO, Byte] =
        emits(batch.content).flatMap(Files[IO].readAll)

      def upload(batch: BatchFile): Stream[IO, Nothing] =
        val name = batch.content.mkString.md5Hash
        exec(logger.info(s"Uploading ${batch.file} ...")) ++
          readConcat(batch)
            .through(
              s3.uploadFileMultipart(
                bucket,
                FileKey(objPrefix.append(name).append(".jsonl.gz")),
                partSize
              )
            )
            .drain ++ exec(storage.clean(batch))

      storage.wips.flatMap(upload)
    }
  }

  final case class S3Credentials(
      accessKey: String,
      secretKey: String
  )
}

trait ObjectStorage {
  def write: Pipe[IO, Byte, Nothing]
  def read(name: String): Stream[IO, Byte]
  def upload: Stream[IO, Nothing]
}
