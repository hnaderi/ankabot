package dev.hnaderi.ankabot
package worker.dal.tables

import cats.effect.IO
import cats.syntax.all.*
import dev.hnaderi.ankabot.worker.Worker.FetchRes
import dev.hnaderi.ankabot.worker.Worker.FetchResultType
import skunk.*

import java.net.URI
import scala.concurrent.duration.FiniteDuration

import InsertionDAO.*
import PGInsertionDAO.*

private final class PGInsertionDAO(session: Session[IO]) extends InsertionDAO {
  private inline def exec[T](l: List[T])(cmd: l.type => Command[l.type]) =
    if l.isEmpty then IO.unit
    else session.prepare(cmd(l)).flatMap(_.execute(l)).void

  override def insertExtractedTechnology(
      values: List[ExtractedTechnology]
  ): IO[Unit] = exec(values)(insertExtracted)

  override def insertPricing(
      values: List[TechnologyPricingInsert]
  ): IO[Unit] = exec(values)(insertPricings)

  override def insertResults(
      results: List[ResultInsert]
  ): IO[List[ResultId]] =
    session
      .prepare(insert(results))
      .flatMap(_.stream(results, 100).compile.toList)

  override def insertPhones(values: List[PhoneInsert]): IO[Unit] =
    exec(values)(PGInsertionDAO.insertPhones)

  override def insertEmails(values: List[EmailInsert]): IO[Unit] =
    exec(values)(PGInsertionDAO.insertEmails)

  override def insertSocials(values: List[SocialsInsert]): IO[Unit] =
    exec(values)(PGInsertionDAO.insertSocials)

  override def insertTechnology(
      values: List[TechnologyInsert]
  ): IO[Unit] = exec(values)(insert)

}

object PGInsertionDAO {
  import skunk.implicits.*
  import skunk.data.Type
  import skunk.codec.all.*

  private val uri: Codec[URI] = varchar.eimap(s =>
    Either.catchNonFatal(URI(s)).leftMap(_.getMessage())
  )(_.toString)
  import scala.jdk.DurationConverters.*
  private val result_id: Codec[ResultId] = int8

  private val duration: Codec[FiniteDuration] =
    interval.imap(_.toScala)(_.toJava)

  private val pricing: Codec[Pricing] =
    `enum`(_.toString, Pricing.mapping.get, Type("pricing"))

  private val socialNetwork: Codec[SocialNetwork] =
    `enum`(_.toString, SocialNetwork.mapping.get, Type("social_network"))

  private val fetchResultType: Codec[FetchResultType] =
    `enum`(_.toString, FetchResultType.mapping.get, Type("fetch_result"))

  private val fetchResult: Codec[FetchRes] =
    (fetchResultType *: int4.opt).to

  private val result: Codec[ResultInsert] =
    (uri *: duration *: fetchResult *: int4 *: int4).to

  private val technology: Codec[TechnologyInsert] =
    (varchar *: varchar.opt *: varchar.opt *: bool.opt *: bool.opt).to

  private def insert(
      results: List[ResultInsert]
  ): Query[results.type, ResultId] =
    sql"""
insert into results ("domain", "duration", "fetch_result", "fetch_status", "total_children", "fetched_children")
values ${result.values.list(results)}
returning id
""".query(result_id)

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
values ${(varchar ~ result_id).values.list(techs)}
""".command

  private def insertPhones(
      techs: List[PhoneInsert]
  ): Command[techs.type] = sql"""
insert into phones (result_id, value)
values ${(result_id ~ varchar).values.list(techs)}
""".command

  private def insertEmails(
      techs: List[EmailInsert]
  ): Command[techs.type] = sql"""
insert into emails (result_id, value)
values ${(result_id ~ varchar).values.list(techs)}
""".command

  private def insertSocials(
      techs: List[SocialsInsert]
  ): Command[techs.type] = sql"""
insert into socials (result_id, "type", value)
values ${(result_id *: socialNetwork *: uri).values.list(techs)}
""".command
}
