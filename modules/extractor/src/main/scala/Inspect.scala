package dev.hnaderi.ankabot

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import fs2.Stream.*
import fs2.io.file.Path
import io.circe.syntax.*
import io.odin.Logger

import java.net.URI

object Inspect {
  def scraped(
      input: Stream[IO, WebsiteData]
  )(using logger: Logger[IO]): Stream[IO, Unit] = for {
    data <- input
    toex <- evals(getPage(data.home)) ++ emits(data.children).flatMap(ch =>
      evals(getPage(ch))
    )
    _ <- eval(IO.println(s"""
url: ${toex.data.url}
scripts: ${toex.page.scripts}
metadata: ${toex.page.metadata}
cookies: ${toex.data.cookies}
headers: ${toex.data.headers}
"""))
  } yield ()

  private def getPage(fetch: FetchResult): IO[Option[ToExtract]] =
    fetch.result match {
      case Left(_) => IO(None)
      case Right(result) =>
        IO.interruptibleMany(JsoupWebPage(result))
          .map(_.toOption.map(ToExtract(_, result)))
    }
}
