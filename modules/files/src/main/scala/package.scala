package dev.hnaderi.ankabot.storage

import fs2.io.file.Path

type Batch = List[Path]
final case class BatchFile(
    file: Path,
    content: Batch
)

type PartFile = Path
