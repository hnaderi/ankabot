package io.aibees.knowledgebase
package worker

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.Chunk
import fs2.Stream
import io.odin.Logger
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

  def apply(
      con: Connection[IO],
      config: Scraper.Config
  )(using
      logger: Logger[IO]
  ): Stream[IO, Nothing] =
    Scraper
      .build(config)
      .flatMap(scrape =>
        resource(con.channel)
          .evalMap(WorkPoolChannel.worker(tasks, _))
          .flatMap(pool =>
            pool.jobs
              .parEvalMapUnordered(config.maxConcurrentPage)(job =>
                job.payload.traverse(scrape) *>
                  pool.processed(job)
              )
          )
      )
      .drain

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
