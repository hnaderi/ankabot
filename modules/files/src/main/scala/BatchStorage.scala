package dev.hnaderi.ankabot.storage

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.kernel.Resource.ExitCase.Succeeded
import cats.effect.std.Queue
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import io.odin.Logger

import Stream.*

trait BatchStorage {
  def write: Pipe[IO, Byte, Nothing]
  def wips: Stream[IO, BatchFile]
  def clean(path: BatchFile): IO[Unit]
}

object BatchStorage {
  private val files = Files[IO]

  def apply(base: Path, threshold: Long, logger: Logger[IO]): IO[BatchStorage] =
    for {
      counter <- FileCounter()
      parts = base / "parts"
      batches = base / "batches"
      _ <- files.createDirectories(parts)
      _ <- files.createDirectories(batches)
      wipQ <- Queue.unbounded[IO, BatchFile]

      storage = BatchStorageImpl(
        threshold,
        logger,
        counter,
        parts = parts,
        batches = batches,
        wipQ
      )
      _ <- storage.init

    } yield storage

  private final class BatchStorageImpl(
      threshold: Long,
      logger: Logger[IO],
      counter: FileCounter,
      parts: Path,
      batches: Path,
      wipQ: Queue[IO, BatchFile]
  ) extends BatchStorage {

    private def cleanTemp(base: Path) = files
      .list(base, "*.tmp")
      .evalMap(files.delete)
      .compile
      .count
      .flatMap(cleanedCount =>
        logger.info(
          s"Removed $cleanedCount incomplete files from last session."
        )
      )

    def init = for {
      _ <- cleanTemp(parts)
      _ <- cleanTemp(batches)

      prevBatches <- files.list(batches).evalMap(getBatch).compile.toVector

      batchesToRemove <- prevBatches
        .traverse(b =>
          b.content
            .traverse(files.exists)
            .map(_.forall(identity))
            .ifF(None, Some(b))
        )
        .map(_.flatten)

      _ <- logger.info(
        s"Found ${batchesToRemove.size} incomplete batches to remove ..."
      )
      _ <- batchesToRemove.traverse(clean)

      partsInBatches = (prevBatches.toSet -- batchesToRemove).flatMap(_.content)

      _ <- logger.info("Loading parts from last session ...")
      loadCount <- files
        .list(parts)
        .filterNot(partsInBatches.contains)
        .evalMap(counter.add)
        .compile
        .count
      loadSize <- counter.size
      _ <- logger.info(
        s"Loaded $loadCount parts from last session, total size: $loadSize"
      )
      _ <- onSizeUpdated(loadSize)
    } yield ()

    private def writeReliable(base: Path): Pipe[IO, Byte, Path] = in =>
      eval(newFile(base, "tmp").product(newFile(base))).flatMap((tmp, out) =>
        in.through(files.writeAll(tmp))
          .onFinalizeCase {
            case Succeeded => files.move(tmp, out)
            case _         => logger.warn(s"Left incomplete file $tmp")
          }
          .as(out)
      )

    override def write: Pipe[IO, Byte, Nothing] = writeReliable(parts)(_)
      .evalMap(counter.add)
      .foreach(onSizeUpdated)

    private def onSizeUpdated(size: Long) =
      if size >= threshold
      then
        logger.info(
          s"Batch threshold reached! [$size/$threshold] Creating a new batch..."
        ) *> counter.get.flatMap(save).flatMap(wipQ.offer)
      else IO.unit

    override def wips: Stream[IO, BatchFile] = fromQueueUnterminated(wipQ)

    private def newFile(base: Path, ext: String = ""): IO[Path] =
      UUIDGen[IO].randomUUID
        .map(_.toString)
        .map(s =>
          if ext.isBlank then base / s
          else base / s"$s.$ext"
        )

    private def getBatch(file: Path): IO[BatchFile] = files
      .readAll(file)
      .through(fs2.text.utf8.decode)
      .through(fs2.text.lines)
      .map(Path(_))
      .compile
      .toList
      .map(BatchFile(file, _))

    private def save(batch: Batch): IO[BatchFile] =
      newFile(batches).flatMap(out =>
        emits(batch)
          .map(_.absolute.toString)
          .intersperse("\n")
          .through(fs2.text.utf8.encode)
          .through(writeReliable(batches))
          .compile
          .drain
          .as(BatchFile(out, batch))
      )

    override def clean(batch: BatchFile): IO[Unit] =
      batch.content.traverse(files.deleteIfExists) >> files.delete(batch.file)

  }

}

trait FileCounter {
  def add(part: PartFile): IO[Long]
  def size: IO[Long]
  def get: IO[Batch]
}

object FileCounter {
  def apply() = for {
    soFar <- IO.ref(0L)
    current <- IO.ref(List.empty[Path])
  } yield new FileCounter {

    override def get: IO[Batch] = soFar.set(0) *> current.getAndSet(Nil)

    override def add(part: PartFile): IO[Long] = Files[IO]
      .size(part)
      .flatMap(size =>
        current.update(_.prepended(part)) *>
          soFar.updateAndGet(_ + size)
      )

    override def size: IO[Long] = soFar.get

  }

}
