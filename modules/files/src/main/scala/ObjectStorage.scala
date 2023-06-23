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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.s3.S3AsyncClient

import java.net.URI

import Stream.*

object ObjectStorage {
  def build(
      endpoint: URI,
      credentials: S3Credentials
  ): Resource[IO, S3[IO]] = Interpreter[IO]
    .S3AsyncClientOpResource(
      S3AsyncClient
        .builder()
        .credentialsProvider(
          StaticCredentialsProvider.create(
            AwsBasicCredentials
              .create(credentials.accessKey, credentials.secretKey)
          )
        )
        .endpointOverride(endpoint)
    )
    .map(S3.create)

  def apply(
      s3: S3[IO],
      storage: BatchStorage,
      bucket: NonEmptyString,
      objPrefix: NonEmptyString,
      partSize: PartSizeMB
  ): Stream[IO, ObjectStorage] = for {
    toUpload <- eval(Queue.unbounded[IO, Path])
    obj = ObjectStorageImpl(
      s3,
      storage,
      BucketName(bucket),
      objPrefix,
      partSize
    )

    _ <- obj.upload.spawn
  } yield obj

  private final class ObjectStorageImpl(
      s3: S3[IO],
      storage: BatchStorage,
      bucket: BucketName,
      objPrefix: NonEmptyString,
      partSize: PartSizeMB
  ) extends ObjectStorage {
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

    def upload: Stream[IO, Nothing] = {

      def readConcat(batch: BatchFile): Stream[IO, Byte] =
        emits(batch.content).flatMap(Files[IO].readAll)

      def upload(batch: BatchFile): Stream[IO, Nothing] =
        readConcat(batch)
          .through(s3.uploadFileMultipart(bucket, FileKey(objPrefix), partSize))
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
}
