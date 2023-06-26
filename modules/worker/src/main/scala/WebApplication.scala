package dev.hnaderi.ankabot
package worker

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import com.comcast.ip4s.Port
import fs2.Pipe
import fs2.Stream
import fs2.Stream.*
import io.odin.Logger
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.multipart.Multipart
import org.http4s.multipart.Multiparts
import org.http4s.multipart.Part
import org.http4s.server.Server
import org.http4s.syntax.all.*

import java.net.URI
import java.net.URL

object WebApplication {
  private object Source extends OptionalQueryParamDecoderMatcher[Uri]("source")
  private object Batch extends OptionalQueryParamDecoderMatcher[Int]("batch")

  private def toURI(using Logger[IO]): Pipe[IO, Byte, URI] =
    _.through(fs2.text.utf8.decode)
      .through(Helpers.decodeSources)

  def routes(publisher: TaskPoolPublisher)(using Logger[IO]) =
    HttpRoutes.of[IO] {
      case req @ POST -> Root :? Source(url) +& Batch(batch) =>
        def submit(input: Stream[IO, Byte]) =
          JobRunner
            .submit(
              publisher,
              input.through(toURI),
              batch.map(_ min 50).getOrElse(10)
            )
            .compile
            .drain *> Ok()

        url match {
          case None =>
            req.decode[Multipart[IO]] { b =>
              submit(emits(b.parts).flatMap(_.body))
            }
          case Some(value) =>
            submit(
              fs2.io
                .readInputStream(
                  IO(new URL(value.toString).openConnection().getInputStream()),
                  4096,
                  true
                )
            )
        }
    }

  def apply(publisher: TaskPoolPublisher, port: Port)(using
      Logger[IO]
  ): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHttpApp(routes(publisher).orNotFound)
      .withPort(port)
      .build

  def upload(
      uri: URI,
      client: Client[IO],
      batchSize: Int
  )(using logger: Logger[IO]): Pipe[IO, URI, Nothing] = in =>
    eval(IO.fromEither(Uri.fromString(uri.toString))).flatMap { uri =>
      import org.http4s.client.dsl.io.*

      val data = in.map(_.toString).through(fs2.text.utf8.encode)
      val req =
        Multiparts
          .forSync[IO]
          .flatMap(_.multipart(Vector(Part(Headers.empty, data))))
          .map(POST(_, uri.withQueryParam("batch", batchSize)))

      exec(
        req
          .flatMap(client.successful)
          .ifM(
            logger.info("Uploaded successfully!"),
            logger.error("Couldn't upload the data!")
          )
      )
    }
}
