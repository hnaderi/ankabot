package io.aibees.knowledgebase

import cats.effect.IO
import org.http4s.Method.GET
import org.http4s.*
import org.http4s.client.Client
import org.http4s.client.dsl.io.*
import scala.concurrent.duration.*

trait Scraper {
  def get(url: Uri): IO[Traffic]
}

object Scraper {
  def apply(client: Client[IO]): Scraper = new {
    override def get(url: Uri): IO[Traffic] = {
      val req = GET(
        url,
        Header(
          "User-Agent",
          "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/111.0"
        )
      )
      client
        .run(req)
        .use { resp =>
          resp.bodyText.compile.string.map(
            Traffic(resp.headers, resp.status, resp.cookies, _)
          )
        }
        .timeout(5.seconds)
    }
  }
}

final case class Traffic(
    headers: Headers,
    status: Status,
    cookies: List[ResponseCookie],
    body: String
)
