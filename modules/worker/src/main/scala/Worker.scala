package io.aibees.knowledgebase
package worker

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.Chunk
import fs2.Stream
import lepus.client.Connection
import lepus.client.MessageCodec
import lepus.protocol.domains.*
import lepus.std.*

import java.net.URI
import scala.concurrent.duration.*

import Stream.*

type Task = Chunk[URI]
type TaskPoolPublisher = WorkPoolServer[IO, Task]

object Worker {
  private given MessageCodec[Task] = MessageCodec[String].eimap(
    _.map(_.toString).mkString_("\n"),
    s =>
      Chunk
        .iterator(s.linesIterator)
        .traverse(s => Either.catchNonFatal(URI(s)))
  )

  private val tasks = WorkPoolDefinition[Task](
    QueueName("tasks"),
    ChannelCodec.plain
  )

  def apply(con: Connection[IO], maxConcurrent: Int = 10): Stream[IO, Nothing] =
    resource(con.channel)
      .evalMap(WorkPoolChannel.worker(tasks, _))
      .flatMap(pool =>
        pool.jobs
          .parEvalMapUnordered(maxConcurrent)(job =>
            process(job) *> pool.processed(job)
          )
      )
      .drain

  private def process(job: Job[Task]): IO[Unit] = IO.println(job.payload)

  def submit(
      tasks: WorkPoolServer[IO, Task],
      sources: Stream[IO, URI],
      batchSize: Int = 10
  ): Stream[IO, Nothing] =
    sources.groupWithin(batchSize, 1.seconds).foreach(tasks.publish)

  def publisher(
      con: Connection[IO]
  ): Resource[IO, TaskPoolPublisher] =
    con.channel.evalMap(WorkPoolChannel.publisher(tasks, _))
}
