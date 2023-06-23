package dev.hnaderi.ankabot
package worker

import cats.effect.IO
import cats.effect.kernel.Resource
import dev.hnaderi.ankabot.db.PersistenceLayer
import dev.hnaderi.ankabot.worker.dal.tables.InsertionDAO
import dev.hnaderi.ankabot.worker.dal.tables.InsertionDAO.*
import dev.hnaderi.ankabot.worker.dal.tables.ResultId
import fs2.Chunk
import io.odin.Logger
import skunk.Session

type Persistence = Chunk[Worker.Result] => IO[Unit]
object Persistence {
  def migrate(pool: Resource[IO, Session[IO]])(using Logger[IO]) = for {
    _ <- pool.use(PersistenceLayer.migrate(_, "db")("types", "tables"))
    tech <- Technology.load
    techI = tech
      .map((k, v) =>
        TechnologyInsert(
          id = k,
          description = v.description,
          website = v.website,
          saas = v.saas,
          oss = v.oss
        )
      )
      .toList

    prices = tech.flatMap((k, v) => v.pricing.map((k, _))).toList

    _ <- InsertionDAO(pool).use(dao =>
      dao.insertTechnology(techI) *> dao.insertPricing(prices)
    )
  } yield ()

  def apply(pool: Resource[IO, Session[IO]])(using
      Logger[IO]
  ): Persistence = data =>
    InsertionDAO(pool).use { dao =>
      val list = data.toList

      dao
        .insertResults(
          list.map(d =>
            ResultInsert(
              domain = d.domain,
              duration = d.duration,
              fetchResult = d.fetchResult,
              totalChildren = d.totalChildren,
              fetchedChildren = d.fetchedChildren
            )
          )
        )
        .map(_.zip(list))
        .flatMap(m =>
          dao.insertEmails(emails(m)) *>
            dao.insertPhones(phones(m)) *>
            dao.insertSocials(socials(m)) *>
            dao.insertExtractedTechnology(technologies(m))
        )

    }

  private def emails(l: List[(ResultId, Worker.Result)]) = l.flatMap((id, d) =>
    d.extracted.toList.flatMap(_.contacts).collect {
      case Contact.Email(value) => (id, value)
    }
  )

  private def socials(l: List[(ResultId, Worker.Result)]) = l.flatMap((id, d) =>
    d.extracted.toList.flatMap(_.contacts).collect {
      case Contact.Social(network, contact) => (id, network, contact)
    }
  )

  private def phones(l: List[(ResultId, Worker.Result)]) = l.flatMap((id, d) =>
    d.extracted.toList.flatMap(_.contacts).collect {
      case Contact.Phone(number) => (id, number)
    }
  )

  private def technologies(l: List[(ResultId, Worker.Result)]) =
    l.flatMap((id, d) =>
      d.extracted.toList.flatMap(_.technologies).map((_, id))
    )
}
