package dev.hnaderi.ankabot.worker

import cats.effect.IO
import cats.effect.kernel.Resource
import dev.hnaderi.ankabot.FetchedData
import dev.hnaderi.ankabot.Storage
import dev.hnaderi.ankabot.storage.BatchStorage
import dev.hnaderi.ankabot.storage.*
import dev.hnaderi.ankabot.worker.S3Persistence.RawData
import dev.hnaderi.ankabot.worker.S3Persistence.TextData
import dev.hnaderi.ankabot.worker.Worker.Result
import fs2.Chunk
import fs2.Stream
import fs2.aws.s3.S3
import fs2.aws.s3.models.Models.BucketName
import fs2.aws.s3.models.Models.PartSizeMB
import fs2.io.file.Path
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.odin.Logger

import java.net.URI

trait S3Persistence {
  def write(results: Chunk[Worker.Result]): Stream[IO, Nothing]
  def readText(name: String): Stream[IO, TextData]
  def readRaw(name: String): Stream[IO, RawData]
  def upload: Stream[IO, Nothing]
}

object S3Persistence {
  final case class Config(
      s3: ObjectStorage.Config,
      workDir: Path,
      rawBatchSize: Int = 1000,
      textBatchSize: Int = 100,
      bucketName: BucketName = BucketName(NS("ankabot")),
      objPrefix: Option[String] = None,
      multipartSize: PartSizeMB = PartSize(5)
  )

  def apply(config: Config)(using Logger[IO]): Resource[IO, S3Persistence] =
    ObjectStorage.build(config.s3).evalMap(from(_, config))

  def from(s3: S3[IO], config: Config)(using Logger[IO]) = for {
    storageText <- BatchStorage(config.workDir / "text", config.textBatchSize)
    obsText <- ObjectStorage(
      s3,
      storageText,
      config.bucketName,
      NS("texts/").append(config.objPrefix),
      config.multipartSize
    )
    storageRaw <- BatchStorage(config.workDir / "raw", config.rawBatchSize)
    obsRaw <- ObjectStorage(
      s3,
      storageRaw,
      config.bucketName,
      NS("raw/").append(config.objPrefix),
      config.multipartSize
    )

  } yield new S3Persistence {
    private val compression = fs2.compression.Compression[IO]

    private def ww[T: Encoder](
        data: Chunk[T],
        obs: ObjectStorage
    ): Stream[IO, Nothing] =
      Stream
        .chunk(data)
        .through(Storage.encodeJsonline)
        .through(compression.gzip())
        .through(obs.write)

    private def rr[T: Decoder](obs: ObjectStorage, name: String) = obs
      .read(name)
      .through(compression.gunzip())
      .flatMap(_.content)
      .through(Storage.decodeJsonline)

    override def write(results: Chunk[Worker.Result]): Stream[IO, Nothing] = {
      val texts = ww(results.map(getTexts), obsText)
      val raw = ww(results.map(getRawData), obsRaw)

      texts.merge(raw)
    }

    override def readText(name: String): Stream[IO, TextData] =
      rr(obsText, name)

    override def readRaw(name: String): Stream[IO, RawData] = rr(obsRaw, name)

    override def upload: Stream[IO, Nothing] =
      obsRaw.upload.merge(obsText.upload)

  }

  private def getTexts(res: Worker.Result) = TextData(
    domain = res.domain,
    texts = res.raw.flatMap(_.page.texts)
  )

  private def getRawData(res: Worker.Result) = RawData(
    domain = res.domain,
    pages = res.raw.map(_.data)
  )

  final case class TextData(
      domain: URI,
      texts: Seq[String]
  ) derives Codec.AsObject

  final case class RawData(
      domain: URI,
      pages: List[FetchedData]
  ) derives Codec.AsObject

}
