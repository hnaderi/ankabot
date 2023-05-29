package io.aibees.knowledgebase

import cats.Show
import cats.syntax.all.*

import scala.concurrent.duration.*

final class Distribution[T] private (
    buckets: Vector[Long],
    counts: Vector[Long],
    toLong: T => Long,
    show: Long => String,
    total: Long = 0L,
    min: Option[Long] = None,
    max: Option[Long] = None
) {
  def add(t: T): Distribution[T] = {
    val value = toLong(t)

    val idx = buckets.indexWhere(_ >= value) match {
      case -1 => counts.size - 1
      case n  => n
    }

    new Distribution(
      counts = counts.updated(idx, counts(idx) + 1),
      buckets = buckets,
      toLong = toLong,
      show = show,
      total = total + value,
      min = min.fold(value)(_ min value).some,
      max = max.fold(value)(_ max value).some
    )
  }

  private final val progressLength = 30

  private def optShow(v: Option[Long]) = v.fold(" - ")(show)

  def print: String = {
    counts.sum.toDouble match {
      case 0 => "No data available yet!"
      case totalCount =>
        val bars = buckets
          .zip(counts)
          .map { (t, c) =>
            val ratio = c / totalCount
            val percent = BigDecimal(ratio * 100)
              .setScale(2, BigDecimal.RoundingMode.HALF_UP)
              .toDouble
            val progress = "||" * (ratio * progressLength).toInt
            val empty = ".." * (progressLength - ratio * progressLength).toInt

            s"$t :\t[$progress$empty] $c ($percent%)"
          }
          .mkString("\n")

        s"""$bars
Min: ${optShow(min)} -> Max: ${optShow(max)}
Total count: $totalCount
Avg: ${show(total / totalCount.toLong)}
Total: ${show(total)}
"""
    }
  }

  override def toString(): String = print
}

object Distribution {
  def apply[T](start: T, step: T, count: Int)(
      toLong: T => Long,
      show: Long => String
  ): Distribution[T] = new Distribution(
    buckets = Vector.fill(count - 1)(toLong(step)).scan(toLong(start))(_ + _),
    counts = Vector.fill(count)(0L),
    toLong = toLong,
    show = show
  )

  def apply(start: Long, step: Long, count: Int): Distribution[Long] =
    apply[Long](start, step, count)(identity, _.toString)

  def buckets[T](t: T, ts: T*)(
      toLong: T => Long,
      show: Long => String
  ): Distribution[T] = {
    val buckets = (Vector(t) ++ ts).map(toLong).sorted
    new Distribution(
      buckets = buckets,
      counts = Vector.fill(buckets.size)(0L),
      toLong = toLong,
      show = show
    )
  }

  def buckets(t: Long, ts: Long*): Distribution[Long] =
    buckets[Long](t, ts: _*)(identity, _.toString)
}

object TimeDistribution {
  def millis(start: Int, step: Int, count: Int): Distribution[FiniteDuration] =
    Distribution(start.millis, step.millis, count)(
      _.toMillis,
      l => s"$l millis"
    )
  def micros(start: Int, step: Int, count: Int): Distribution[FiniteDuration] =
    Distribution(start.micros, step.micros, count)(
      _.toMicros,
      l => s"$l micros"
    )
}
