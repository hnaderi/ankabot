package dev.hnaderi.ankabot.storage

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Resource.ExitCase.Succeeded
import cats.effect.std.Mutex
import cats.effect.std.Queue
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import io.odin.Logger

import scala.concurrent.duration.*

import Stream.*

trait BatchStorage {
  def write: Pipe[IO, Byte, Nothing]
  def wips: Stream[IO, BatchFile]
  def clean(path: BatchFile): IO[Unit]
  def flush: IO[Unit]
}

object BatchStorage {
  private val files = Files[IO]

  def apply(
      base: Path,
      threshold: Long,
      maxToleratedWriteLag: FiniteDuration = 10.minutes
  )(using Logger[IO]): IO[BatchStorage] =
    for {
      counter <- FileCounter()
      parts = base / "parts"
      batches = base / "batches"
      _ <- files.createDirectories(parts)
      _ <- files.createDirectories(batches)
      wipQ <- Queue.unbounded[IO, BatchFile]
      lastWrite <- IO.ref(Option.empty[FiniteDuration])

      storage = BatchStorageImpl(
        threshold,
        counter,
        parts = parts,
        batches = batches,
        wipQ,
        lastWrite,
        maxToleratedWriteLag
      )
      _ <- storage.init

    } yield storage

  private final class BatchStorageImpl(
      threshold: Long,
      counter: Resource[IO, FileCounter],
      parts: Path,
      batches: Path,
      wipQ: Queue[IO, BatchFile],
      lastWrite: Ref[IO, Option[FiniteDuration]],
      maxToleratedWrite: FiniteDuration
  )(using logger: Logger[IO])
      extends BatchStorage {

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

    def init = counter.use(counter =>
      for {
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

        batchesToLoad = prevBatches.toSet -- batchesToRemove
        _ <- logger.info(
          s"Found ${prevBatches.size} complete batches from previous session ..."
        )
        _ <- batchesToLoad.toList.traverse(wipQ.offer)

        partsInBatches = batchesToLoad.flatMap(_.content)

        _ <- logger.info("Loading parts from previous session ...")
        loadCount <- files
          .list(parts)
          .filterNot(partsInBatches.contains)
          .evalMap(addPart(counter, _))
          .compile
          .count
        loadSize <- counter.size
        _ <- logger.info(
          s"Loaded $loadCount parts from previous session, total size: $loadSize"
        )
      } yield ()
    )

    private def writeReliable(base: Path): Pipe[IO, Byte, Path] = in =>
      eval(newFile(base, "tmp").product(newFile(base))).flatMap((tmp, out) =>
        exec(logger.debug(s"Writing $tmp")) ++
          in.through(files.writeAll(tmp))
            .onFinalizeCase {
              case Succeeded =>
                files.move(tmp, out) *> logger.debug(s"File written: $out!")
              case _ => logger.warn(s"Left incomplete file $tmp")
            } ++ emit(out)
      )

    override def write: Pipe[IO, Byte, Nothing] = writeReliable(parts)(_)
      .foreach(f => counter.use(addPart(_, f)))
      .onFinalize(IO.realTime.map(Some(_)).flatMap(lastWrite.set))

    private def addPart(fc: FileCounter, file: Path) =
      fc
        .add(file)
        .flatMap(size =>
          if size >= threshold then
            logger.info(
              s"Batch threshold reached! [$size/$threshold] Creating a new batch..."
            ) *> trigger(fc)
          else logger.debug(s"size: $size/threshold: $threshold")
        )

    private def trigger(fc: FileCounter) =
      fc.get.flatMap(save).flatMap(wipQ.offer)

    override def flush: IO[Unit] = counter.use(counter =>
      counter.size.flatMap(size =>
        if size > 0 then
          logger.info(s"Forcing batch creation, [$size/$threshold]") *> trigger(
            counter
          )
        else IO.unit
      )
    )

    override def wips: Stream[IO, BatchFile] =
      fromQueueUnterminated(wipQ).concurrently(
        awakeEvery[IO](5.second).foreach { _ =>
          IO.realTime.flatMap(now =>
            lastWrite
              .modify {
                case Some(value) if now - value > maxToleratedWrite =>
                  (None, true)
                case other => (other, false)
              }
              .ifM(
                logger
                  .info(
                    "Last written part is too old! triggering a flush..."
                  ) *>
                  flush,
                IO.unit
              )
          )
        }
      )

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
      emits(batch)
        .map(_.absolute.toString)
        .intersperse("\n")
        .through(fs2.text.utf8.encode)
        .through(writeReliable(batches))
        .compile
        .lastOrError
        .map(BatchFile(_, batch))

    override def clean(batch: BatchFile): IO[Unit] =
      logger.info(s"Cleaning ${batch.file}") *>
        batch.content.traverse(files.deleteIfExists) >> files.delete(batch.file)

  }

}

trait FileCounter {
  def add(part: PartFile): IO[Long]
  def get: IO[Batch]
  def size: IO[Long]
}

object FileCounter {
  private def build() = for {
    soFar <- IO.ref(0L)
    current <- IO.ref(List.empty[Path])
  } yield new FileCounter {

    override def get: IO[Batch] = soFar.set(0) *> current.getAndSet(Nil)

    override def size: IO[Long] = soFar.get

    override def add(part: PartFile): IO[Long] = Files[IO]
      .size(part)
      .flatMap(size =>
        current.update(_.prepended(part)) *>
          soFar.updateAndGet(_ + size)
      )
  }

  def apply(): IO[Resource[IO, FileCounter]] = for {
    fc <- build()
    m <- Mutex[IO]
  } yield m.lock.as(fc)

}
