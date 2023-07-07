package dev.hnaderi.ankabot
package storage

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.syntax.all.*
import eu.timepit.refined.types.string.NonEmptyString
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import fs2.aws.s3.S3
import fs2.aws.s3.models.Models.BucketName
import fs2.aws.s3.models.Models.FileKey
import fs2.aws.s3.models.Models.PartSizeMB
import fs2.compression.Compression
import fs2.io.file.Files
import fs2.io.file.Path
import io.laserdisc.pure.s3.tagless.Interpreter
import io.laserdisc.pure.s3.tagless.S3AsyncClientOp
import io.odin.Logger
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient

import java.net.URI

import Stream.*

object ObjectStorageWriter {
  final case class Config(
      address: URI,
      credentials: S3Credentials,
      pathStyle: Boolean = false,
      region: Option[String] = None
  )

  def buildLowLevel(config: Config): Resource[IO, S3AsyncClientOp[IO]] = {
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
  }

  def build(config: Config): Resource[IO, S3[IO]] =
    buildLowLevel(config).map(S3.create)

  def apply(
      s3: S3[IO],
      storage: BatchStorage,
      bucket: BucketName,
      objPrefix: NonEmptyString,
      partSize: PartSizeMB
  )(using logger: Logger[IO]): IO[ObjectStorageWriter] = for {

    toUpload <- Queue.unbounded[IO, Path]

    newline <- emit("\n")
      .through(fs2.text.utf8.encode)
      .through(Compression[IO].gzip())
      .compile
      .to(Chunk)
  } yield new ObjectStorageWriter {

    override def write: Pipe[IO, Byte, Nothing] = storage.write

    override def upload: Stream[IO, Nothing] = {

      def readConcat(batch: BatchFile): Stream[IO, Byte] =
        emits(batch.content)
          .map(Files[IO].readAll)
          .intersperse(chunk(newline))
          .flatten

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

trait ObjectStorageWriter {
  def write: Pipe[IO, Byte, Nothing]
  def upload: Stream[IO, Nothing]
}
