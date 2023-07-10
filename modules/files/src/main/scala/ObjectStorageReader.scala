package dev.hnaderi.ankabot.storage

import cats.effect.IO
import cats.effect.kernel.Resource
import eu.timepit.refined.types.string.NonEmptyString
import fs2.Pipe
import fs2.Stream
import fs2.aws.s3.S3
import fs2.aws.s3.models.Models.BucketName
import fs2.aws.s3.models.Models.FileKey
import io.laserdisc.pure.s3.tagless.S3AsyncClientOp
import org.reactivestreams.FlowAdapters
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.S3Object

import scala.jdk.CollectionConverters.*

import Stream.*

trait ObjectStorageReader {
  def read(prefix: Option[String] = None): Stream[IO, Byte]
  def read(objectName: String): Stream[IO, Byte]
}

object ObjectStorageReader {
  def from(
      s3: S3AsyncClientOp[IO],
      bucket: BucketName
  ): ObjectStorageReader = new ObjectStorageReader {

    private val client = S3.create(s3)

    private def reqFor(prefix: Option[String]) = {
      val base = ListObjectsV2Request.builder().bucket(bucket.value.value)

      prefix.fold(base)(base.prefix(_)).build()
    }

    import fs2.interop.flow.fromPublisher

    private def listList(prefix: Option[String]): Stream[IO, S3Object] =
      eval(s3.listObjectsV2Paginator(reqFor(prefix)))
        .map(FlowAdapters.toFlowPublisher(_))
        .flatMap(fromPublisher[IO](_, 100))
        .map(_.contents().asScala)
        .flatMap(emits(_))

    private val decomp: Pipe[IO, Byte, Byte] = in =>
      resource(fs2.io.process.ProcessBuilder("gzip", "-d", "-").spawn[IO])
        .flatMap(p => p.stdin(in) merge p.stdout)

    private def readObj(name: String) = client
      .readFileMultipart(
        bucket,
        FileKey(NonEmptyString.unsafeFrom(name)),
        PartSize(10)
      )
      .through(decomp)

    override def read(objectName: String): Stream[IO, Byte] = readObj(
      objectName
    )

    override def read(prefix: Option[String]): Stream[IO, Byte] =
      listList(prefix).map(_.key()).flatMap(readObj)
  }

  def apply(
      config: ObjectStorageWriter.Config,
      bucket: BucketName
  ): Resource[IO, ObjectStorageReader] =
    ObjectStorageWriter.buildLowLevel(config).map(from(_, bucket))
}
