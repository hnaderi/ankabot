package dev.hnaderi.ankabot

import eu.timepit.refined.types.string.NonEmptyString
import fs2.io.file.Path

enum StoragePath {
  case Local(path: Path)
  case S3(bucket: NonEmptyString, file: NonEmptyString)
}
