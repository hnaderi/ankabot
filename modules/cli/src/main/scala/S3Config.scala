package dev.hnaderi.ankabot

import cats.syntax.all.*
import com.monovore.decline.Argument
import com.monovore.decline.Opts
import dev.hnaderi.ankabot.storage.ObjectStorage
import dev.hnaderi.ankabot.worker.S3Persistence
import eu.timepit.refined.types.string.NonEmptyString
import fs2.aws.s3.models.Models.BucketName
import fs2.aws.s3.models.Models.PartSizeMB
import fs2.io.file.Path
import pureconfig.ConfigReader
import pureconfig.error.UserValidationFailed
import pureconfig.generic.derivation.default._

import java.net.URI
import dev.hnaderi.ankabot.storage.PartSize
import dev.hnaderi.ankabot.storage.NS

enum S3Config {
  case Enabled(config: S3Persistence.Config)
  case Disabled
}
object S3Config {
  private val cred = (
    opt[String](
      name = "s3-access-key",
      env = "S3_ACCESS_KEY",
      help = "S3 access key"
    ),
    opt[String](
      name = "s3-secret-key",
      env = "S3_SECRET_KEY",
      help = "S3 secret key"
    )
  ).mapN(ObjectStorage.S3Credentials(_, _))

  def s3 = (
    opt[URI](
      name = "s3-address",
      env = "S3_ADDRESS",
      help = "s3 API address",
      default = URI("http://localhost:9000")
    ),
    cred,
    Opts.flag("s3-pathstyle", "If server uses path style, e.g. minio").orFalse,
    opt[String](
      name = "s3-region",
      env = "S3_REGION",
      help = "S3 region"
    ).orNone
  ).mapN(ObjectStorage.Config(_, _, _, _))

  private given ConfigReader[PartSizeMB] = ConfigReader.intConfigReader.emap(
    PartSizeMB.from(_).leftMap(UserValidationFailed(_))
  )
  private given ConfigReader[BucketName] = ConfigReader.stringConfigReader
    .emap(NonEmptyString.from(_).leftMap(UserValidationFailed(_)))
    .map(BucketName(_))
  private given ConfigReader[Path] =
    ConfigReader.pathConfigReader.map(Path.fromNioPath(_))

  given ConfigReader[ObjectStorage.S3Credentials] = ConfigReader.derived
  given obsConfigReader: ConfigReader[ObjectStorage.Config] =
    ConfigReader.derived
  given ConfigReader[S3Persistence.Config] = ConfigReader.derived

  private given Argument[BucketName] =
    Argument
      .from("bucket name")(NonEmptyString.from(_).toValidatedNel)
      .map(BucketName(_))
  private given Argument[PartSizeMB] = Argument.from("part size")(
    Argument[Int].read(_).andThen(PartSizeMB.from(_).toValidatedNel)
  )
  private def persistence = (
    s3,
    opt[Path](
      name = "s3-persistence-path",
      env = "S3_P_PATH",
      help = "local workdir used for intermediate files to be uploaded to s3"
    ),
    opt[Int](
      name = "s3-persistence-raw-size",
      env = "S3_PERSISTENCE_RAW_SIZE",
      help = "batch size for s3 raw data persistence",
      default = 100
    ),
    opt[Int](
      name = "s3-persistence-text-size",
      env = "S3_PERSISTENCE_TEXT_SIZE",
      help = "batch size for s3 text data persistence",
      default = 100
    ),
    opt[BucketName](
      name = "s3-persistence-bucket",
      env = "S3_PERSISTENCE_BUCKET",
      help = "s3 persistence bucket name",
      default = BucketName(NS("ankabot"))
    ),
    opt[String](
      name = "s3-persistence-prefix",
      env = "S3_PERSISTENCE_PREFIX",
      help = "s3 persistence object name prefix"
    ).orNone,
    opt[PartSizeMB](
      name = "s3-persistence-multipart-size",
      env = "S3_PERSISTENCE_MULTIPART_SIZE",
      help = "s3 persistence multipart size",
      default = PartSize(5)
    ),
  ).mapN(S3Persistence.Config(_, _, _, _, _, _, _))

  def opts = (Opts
    .flag("s3-enabled", "Is s3 upload enabled?") *> persistence)
    .map(S3Config.Enabled(_))
    .withDefault(S3Config.Disabled)

}
