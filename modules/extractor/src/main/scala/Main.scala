package io.aibees.knowledgebase

import cats.effect.IO
import fs2.Stream
import io.circe.syntax.*

object Main extends CMDApp(RunOptions.cmd) {

  override def app(opt: RunOptions): Stream[IO, Unit] =
    opt.input.fold(Storage.stdinResults)(Storage.load).evalMap { data =>
      data.result.flatMap(JsoupWebPage(_)) match {
        case Right(page) =>
          // IO.println(page.links.filter(interesting))
          val result = Extractors
            .all(page)
            .contacts
            .asJson
            .noSpaces

          IO.println(s"""
Extracted from ${data.source}
Links: ${page.links.mkString("\n")}
${result}
""")
        case Left(value) => IO.unit
      }
    }

  private def interesting(link: Link): Boolean = {
    val text = link.text.toLowerCase
    val url = link.value.toString

    text.contains("contact") || text.contains("about")
  }
}
