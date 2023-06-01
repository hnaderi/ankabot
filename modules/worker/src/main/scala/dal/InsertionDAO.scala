package io.aibees.knowledgebase
package worker.dal.tables

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import skunk.Session

import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

import InsertionDAO.*

type ResultId = Long
type TechnologyId = String

trait InsertionDAO {
  def insertTechnology(values: List[TechnologyInsert]): IO[Unit]

  def insertExtractedTechnology(
      values: List[ExtractedTechnology]
  ): IO[Unit]

  def insertPricing(values: List[TechnologyPricingInsert]): IO[Unit]

  def insertSocials(values: List[SocialsInsert]): IO[Unit]

  def insertEmails(values: List[EmailInsert]): IO[Unit]

  def insertPhones(values: List[PhoneInsert]): IO[Unit]

  def insertResults(results: List[ResultInsert]): IO[List[ResultId]]
}

object InsertionDAO {
  final case class ResultInsert(
      domain: URI,
      duration: FiniteDuration,
      success: Boolean,
      totalChildren: Int,
      fetchedChildren: Int
  )

  final case class TechnologyInsert(
      id: TechnologyId,
      description: Option[String] = None,
      website: Option[String] = None,
      saas: Option[Boolean] = None,
      oss: Option[Boolean] = None
  )

  type ExtractedTechnology = (TechnologyId, ResultId)
  type SocialsInsert = (ResultId, SocialNetwork, URI)
  type PhoneInsert = (ResultId, String)
  type EmailInsert = (ResultId, String)
  type TechnologyPricingInsert = (TechnologyId, Pricing)

  def apply(pool: Resource[IO, Session[IO]]): Resource[IO, InsertionDAO] =
    pool.flatTap(_.transaction).map(PGInsertionDAO(_))

  def apply(session: Session[IO]): InsertionDAO = PGInsertionDAO(session)
}
