package dev.hnaderi.ankabot
package worker

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import io.odin.Logger
import lepus.client.Connection

import java.net.URI
import scala.concurrent.duration.*

object Worker {
  def apply(
      con: Connection[IO],
      persist: Persistence,
      config: Scraper.Config,
      extractor: Extractor
  )(using
      logger: Logger[IO]
  ): Stream[IO, Unit] = for {
    scrape <- Scraper.build(config)
    extract = Worker.extract(extractor)

    _ <- JobRunner(con, config.maxConcurrentPage) { job =>
      for {
        scraped <- job.payload.traverse(scrape)

        extracted <- scraped.traverse(extract)

        _ <- persist(extracted)
      } yield ()
    }
  } yield ()

  final case class Result(
      domain: URI,
      duration: FiniteDuration,
      fetchResult: FetchRes,
      extracted: Option[ExtractedData] = None,
      totalChildren: Int = 0,
      fetchedChildren: Int = 0
  )
  enum FetchResultType {
    case Success
    case BadStatus
    case Timeout
    case Failure
  }

  object FetchResultType {
    val mapping: Map[String, FetchResultType] =
      FetchResultType.values.map(p => p.toString -> p).toMap
  }

  final case class FetchRes(result: FetchResultType, status: Option[Int])

  private def extract(
      extractor: Extractor
  )(data: WebsiteData): IO[Result] =
    Extractor.getPage(data.home).flatMap {
      case None =>
        Result(
          domain = data.home.source,
          duration = data.time,
          fetchResult = fetchResult(data.home)
        ).pure
      case Some(home) =>
        for {
          children <- data.children.traverse(Extractor.getPage).map(_.flatten)
          extracted <- (home :: children).traverse(extractor)

        } yield Result(
          domain = home.page.address,
          duration = data.time,
          fetchResult =
            FetchRes(FetchResultType.Success, home.data.status.some),
          extracted = Some(extracted.combineAll),
          totalChildren = home.page.childPages.size,
          fetchedChildren = children.size
        )
    }

  private def fetchResult(result: FetchResult): FetchRes = result.result match {
    case Right(value) => FetchRes(FetchResultType.Success, value.status.some)
    case Left(FetchError.Timeout) => FetchRes(FetchResultType.Timeout, None)
    case Left(FetchError.Failed)  => FetchRes(FetchResultType.Failure, None)
    case Left(FetchError.BadStatus(status, _)) =>
      FetchRes(FetchResultType.BadStatus, status.some)
  }
}
