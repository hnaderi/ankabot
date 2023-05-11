package io.aibees.knowledgebase

import cats.Show
import cats.syntax.all.*
import scala.concurrent.duration.*

trait Distribution[T] {
  def add(t: T): Distribution[T]
  def print: String
}

object Distribution {
  private final class DistributionImpl[T: Show](
      buckets: Vector[Long],
      counts: Vector[Long],
      total: Long = 0L,
      min: Option[Long] = None,
      max: Option[Long] = None
  )(using L: LongRepresentable[T])
      extends Distribution[T] {
    def add(t: T): Distribution[T] = {
      val value = L.toLong(t)

      val idx = buckets.indexWhere(_ >= value) match {
        case -1 => counts.size - 1
        case n  => n
      }

      new DistributionImpl(
        counts = counts.updated(idx, counts(idx) + 1),
        buckets = buckets,
        total = total + value,
        min = min.fold(value)(_ min value).some,
        max = max.fold(value)(_ max value).some
      )
    }
    private def optShow(v: Option[Long]) = v.fold(" - ")(L.fromLong(_).show)

    private final val progressLength = 30
    override def print: String = {
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
Avg: ${L.fromLong(total / totalCount.toLong).show}
Total: ${L.fromLong(total).show}
"""
      }
    }

    override def toString(): String = print

  }

  def apply[T: Show](start: T, step: T, count: Int)(using
      L: LongRepresentable[T]
  ): Distribution[T] = new DistributionImpl(
    buckets =
      Vector.fill(count - 1)(L.toLong(step)).scan(L.toLong(start))(_ + _),
    counts = Vector.fill(count)(0L)
  )
  def buckets[T: Show](t: T, ts: T*)(using
      L: LongRepresentable[T]
  ): Distribution[T] = {
    val buckets = (Vector(t) ++ ts).map(L.toLong).sorted
    new DistributionImpl(
      buckets = buckets,
      counts = Vector.fill(buckets.size)(0L)
    )
  }
}

trait LongRepresentable[T] {
  def toLong(t: T): Long
  def fromLong(l: Long): T
}

object LongRepresentable {
  given LongRepresentable[Long] = new {
    inline def toLong(t: Long): Long = t
    inline def fromLong(l: Long): Long = l
  }

  given LongRepresentable[FiniteDuration] = new {
    inline def toLong(t: FiniteDuration): Long = t.toMillis
    inline def fromLong(l: Long): FiniteDuration = l.millis
  }
}

object Main extends App {
  import scala.util.Random
  val dist = Distribution.buckets[Long](0, 1, 5, 10, 50, 100)
  // Distribution(0.second, 2.seconds, 10)

  val rng = Random()
  val newDist =
    List.fill(100000)(rng.between(0, 20 * 1000)).foldLeft(dist)(_ add _)
  println(newDist)
}
