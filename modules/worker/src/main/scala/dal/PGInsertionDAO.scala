package io.aibees.knowledgebase
package worker.dal.tables

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.all.*
import skunk.*

import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

import InsertionDAO.*
import PGInsertionDAO.*

private final class PGInsertionDAO(session: Session[IO]) extends InsertionDAO {

  override def insertExtractedTechnology(
      values: List[ExtractedTechnology]
  ): IO[Unit] =
    session.prepare(insertExtracted(values)).flatMap(_.execute(values)).void

  override def insertPricing(
      values: List[TechnologyPricingInsert]
  ): IO[Unit] =
    session.prepare(insertPricings(values)).flatMap(_.execute(values)).void

  override def insertResults(
      results: List[ResultInsert]
  ): IO[List[ResultId]] =
    session
      .prepare(insert(results))
      .flatMap(_.stream(results, 100).compile.toList)

  override def insertPhones(values: List[PhoneInsert]): IO[Unit] =
    session
      .prepare(PGInsertionDAO.insertPhones(values))
      .flatMap(_.execute(values))
      .void

  override def insertEmails(values: List[EmailInsert]): IO[Unit] =
    session
      .prepare(PGInsertionDAO.insertEmails(values))
      .flatMap(_.execute(values))
      .void

  override def insertSocials(values: List[SocialsInsert]): IO[Unit] =
    session
      .prepare(PGInsertionDAO.insertSocials(values))
      .flatMap(_.execute(values))
      .void

  override def insertTechnology(
      values: List[TechnologyInsert]
  ): IO[Unit] =
    session.prepare(insert(values)).flatMap(_.execute(values)).void

}

object PGInsertionDAO {
  import skunk.implicits.*
  import skunk.data.Type
  import skunk.codec.all.*

  private val uri: Codec[URI] = varchar.eimap(s =>
    Either.catchNonFatal(URI(s)).leftMap(_.getMessage())
  )(_.toString)
  import scala.jdk.DurationConverters.*

  private val duration: Codec[FiniteDuration] =
    interval.imap(_.toScala)(_.toJava)

  private val pricing: Codec[Pricing] =
    `enum`(_.toString, Pricing.mapping.get, Type("pricing"))

  private val socialNetwork: Codec[SocialNetwork] =
    `enum`(_.toString, SocialNetwork.mapping.get, Type("social_network"))

  private val result: Codec[ResultInsert] =
    (uri ~ timestamptz ~ duration ~ bool ~ int4 ~ int4).gimap

  private val technology: Codec[TechnologyInsert] =
    (varchar ~ varchar.opt ~ varchar.opt ~ bool.opt ~ bool.opt).gimap

  private def insert(results: List[ResultInsert]): Query[results.type, UUID] =
    sql"""
insert into results ("domain", "date", "duration", "success", "total_children", "fetched_children")
values ${result.values.list(results)}
returning id
""".query(uuid)

  private def insert(techs: List[TechnologyInsert]): Command[techs.type] =
    sql"""
insert into technologies ("id", "description", "website", "saas", "oss")
values ${technology.values.list(techs)}
on conflict (id) do update
  set description = excluded.description,
      website = excluded.website,
      saas = excluded.saas,
      oss = excluded.oss
""".command

  private def insertPricings(
      techs: List[TechnologyPricingInsert]
  ): Command[techs.type] = sql"""
insert into technology_pricing (technology_id, value)
values ${(varchar ~ pricing).values.list(techs)}
""".command

  private def insertExtracted(
      techs: List[ExtractedTechnology]
  ): Command[techs.type] = sql"""
insert into extracted_technologies (technology_id, result_id)
values ${(varchar ~ uuid).values.list(techs)}
""".command

  private def insertPhones(
      techs: List[PhoneInsert]
  ): Command[techs.type] = sql"""
insert into phones (result_id, value)
values ${(uuid ~ varchar).values.list(techs)}
""".command

  private def insertEmails(
      techs: List[EmailInsert]
  ): Command[techs.type] = sql"""
insert into emails (result_id, value)
values ${(uuid ~ varchar).values.list(techs)}
""".command

  private def insertSocials(
      techs: List[SocialsInsert]
  ): Command[techs.type] = sql"""
insert into socials (result_id, "type", value)
values ${(uuid *: socialNetwork *: uri).values.list(techs)}
""".command
}
