import dev.hnaderi.ankabot.Storage
import fs2.Stream
import fs2.Stream.*
import io.circe.Codec
import munit.CatsEffectSuite

class StorageSuite extends CatsEffectSuite {
  final case class Data(value: Int) derives Codec.AsObject

  test("Jsonline must also decode several json objects in a single line") {
    Stream("""{"value":1}{"value":2}
{"value": 3}""")
      .through(fs2.text.utf8.encode)
      .through(Storage.decodeJsonline[Data])
      .compile
      .toList
      .assertEquals(
        List.range(1, 4).map(Data(_))
      )
  }
}
