import cats.effect.IO
import dev.hnaderi.ankabot.Storage
import fs2.Pipe
import fs2.Stream
import fs2.Stream.*
import fs2.compression.Compression
import io.circe.Codec
import munit.CatsEffectSuite

class StorageSuite extends CatsEffectSuite {
  final case class Data(value: Int) derives Codec.AsObject

  test("Jsonline must also decode several json objects in a single line") {
    Stream(
      """{"value":1}{"value":2}
{"value": 3}
""",
      """
{"value":4}{"value":5}
"""
    )
      .through(fs2.text.utf8.encode)
      .through(Storage.decodeJsonline[Data])
      .compile
      .toList
      .assertEquals(
        List.range(1, 6).map(Data(_))
      )
  }

  test("concatenated gzips") {
    val comp: Pipe[IO, String, Byte] =
      _.through(fs2.text.utf8.encode)
        .through(Compression[IO].gzip())

    val decomp: Pipe[IO, Byte, String] = in =>
      resource(fs2.io.process.ProcessBuilder("gzip", "-d", "-").spawn[IO])
        .flatMap(p => p.stdin(in) merge p.stdout)
        .through(fs2.text.utf8.decode)

    val newline = emit("\n").through(comp)
    val a = Stream("""ab
c
""").through(comp)
    val b = Stream(
      """
de
"""
    ).through(comp)

    val stored = a ++ newline ++ b

    stored
      .through(decomp)
      .compile
      .string
      .assertEquals("""ab
c


de
""")
  }
}
