package dev.hnaderi.ankabot
package worker

import cats.effect.IO
import cats.effect.kernel.Resource
import dev.hnaderi.ankabot.storage.BatchStorage
import dev.hnaderi.ankabot.storage.*
import dev.hnaderi.ankabot.worker.Worker.Result
import fs2.Chunk
import fs2.Stream
import fs2.aws.s3.S3
import fs2.aws.s3.models.Models.BucketName
import fs2.aws.s3.models.Models.PartSizeMB
import fs2.io.file.Path
import io.circe.Encoder
import io.odin.Logger

import scala.concurrent.duration.*

trait S3Persistence {
  def write(results: Chunk[Worker.Result]): Stream[IO, Nothing]
  def upload: Stream[IO, Nothing]
}

object S3Persistence {
  final case class Config(
      s3: ObjectStorageWriter.Config,
      workDir: Path,
      rawBatchSize: Int = 1000,
      textBatchSize: Int = 100,
      maxWriteLag: FiniteDuration = 10.minutes,
      bucketName: BucketName = BucketName(NS("ankabot")),
      objPrefix: Option[String] = None,
      multipartSize: PartSizeMB = PartSize(5)
  )

  def apply(config: Config)(using Logger[IO]): Resource[IO, S3Persistence] =
    ObjectStorageWriter.build(config.s3).evalMap(from(_, config))

  def from(s3: S3[IO], config: Config)(using Logger[IO]) = for {
    storageText <- BatchStorage(
      config.workDir / "text",
      config.textBatchSize.MB,
      config.maxWriteLag
    )
    obsText <- ObjectStorageWriter(
      s3,
      storageText,
      config.bucketName,
      NS("text/").prepend(config.objPrefix),
      config.multipartSize
    )
    storageRaw <- BatchStorage(
      config.workDir / "raw",
      config.rawBatchSize.MB,
      config.maxWriteLag
    )
    obsRaw <- ObjectStorageWriter(
      s3,
      storageRaw,
      config.bucketName,
      NS("raw/").prepend(config.objPrefix),
      config.multipartSize
    )

  } yield new S3Persistence {
    private val compression = fs2.compression.Compression[IO]

    private def ww[T: Encoder](
        data: Chunk[T],
        obs: ObjectStorageWriter
    ): Stream[IO, Nothing] =
      Stream
        .chunk(data)
        .through(Storage.encodeJsonline)
        .through(compression.gzip())
        .through(obs.write)

    override def write(results: Chunk[Worker.Result]): Stream[IO, Nothing] = {
      val texts = ww(results.map(getTexts), obsText)
      val raw = ww(results.map(getRawData), obsRaw)

      texts.merge(raw)
    }

    override def upload: Stream[IO, Nothing] =
      obsRaw.upload.merge(obsText.upload)

  }

  private def getTexts(res: Worker.Result) = TextData(
    domain = res.domain,
    texts = res.raw.flatMap(
      _.page.texts.filterNot(s => s.isEmpty || s.isBlank).map(_.strip)
    )
  )

  private def getRawData(res: Worker.Result) = RawData(
    domain = res.domain,
    pages = res.raw.map(_.data)
  )

}
