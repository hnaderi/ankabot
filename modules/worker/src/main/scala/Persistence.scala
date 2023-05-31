package io.aibees.knowledgebase
package worker

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.Chunk
import io.aibees.knowledgebase.db.PersistenceLayer
import io.aibees.knowledgebase.worker.dal.tables.InsertionDAO
import io.aibees.knowledgebase.worker.dal.tables.InsertionDAO.TechnologyInsert
import io.odin.Logger
import skunk.Session

type Persistence = Chunk[WebsiteData] => IO[Unit]
object Persistence {
  def migrate(session: Session[IO])(using Logger[IO]) =
    PersistenceLayer.migrate(session, "db")("0") *> Technology.load.flatMap {
      m =>
        val dao = InsertionDAO(session)
        val tech = m
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

        val prices = m.flatMap((k, v) => v.pricing.map((k, _))).toList

        dao.insertTechnology(tech) *> dao.insertPricing(prices)
    }

  def apply(pool: Resource[IO, Session[IO]])(using
      Logger[IO]
  ): Persistence = data =>
    InsertionDAO(pool).use { dao =>
      val a = data.map(_.home)
      ???
    }

}
