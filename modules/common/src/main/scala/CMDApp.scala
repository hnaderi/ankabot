package dev.hnaderi.ankabot

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import com.monovore.decline.Command
import io.odin.Logger

abstract class CMDApp[T](cmd: Command[T]) extends IOApp {
  protected given logger: Logger[IO]

  override final def run(args: List[String]): IO[ExitCode] =
    IO.println(CMDApp.bannerColored).as(cmd.parse(args, sys.env)).flatMap {
      case Left(value) => IO.println(value).as(ExitCode.Success)
      case Right(value) =>
        for {
          start <- IO.realTime
          _ <- app(value)
            .onFinalize(
              IO.realTime
                .map(_ - start)
                .map(_.toCoarsest)
                .flatMap(time => logger.info(s"Process took $time"))
            )
            .compile
            .drain
        } yield ExitCode.Success
    }

  def app(t: T): fs2.Stream[IO, Unit]
}

object CMDApp {

  extension (inline banner: String) {
    inline def colorize(inline char: String, inline colors: String): String = {
      val startPattern = s"([^$char])$char".r
      val endPattern = s"$char([^$char])".r

      endPattern.replaceAllIn(
        startPattern.replaceAllIn(banner, s"$$1$colors$char"),
        s"$char${Console.RESET}$$1"
      )
    }
  }

  private inline val banner = """

                ,, ,,                     ,,,,,,,,,               ,
             ,,       ,,        ,,,*(((((((((,,,,,,.           ,,  ,,
          ,,             ,,,,,(((((((((((((((((((((,,      .,,       ,
       ,,               ,,,,*(((((((((((((((((((((((((,,,,            ,,
   ,,,                  ,,,(((((((*,((((((((((((/,/(((((,,              ,
               ,,,    ,,,*((((,*@@@@@@@,((((,,@@@@@@@,,(((,,,            ,,
              ,     ,,(*/((((,@@@@@,,&&&,*(,,@@@@,,&&&,,(((,,             .,
            ,,      ,  ,(((((,@@@@@,&&&&,*(,@@@@@,,&&&,,(((,,,    ,,,       ,,
          ,,          .,,,*((/,@@@@@@@@,,(((,(@@@@@@@(,((((,,,       ,       .,
         ,,             ,,,(((((/,,,,,(((((((((,,,,,(((((,,,,,        ,
       ,,           ,,,,,  ,(/((((((((((((,((((((((((((((,,            ,
      ,            ,,      ,,,*(((((((((((((((((((((((,,,,,,            ,
                  ,,           ,,,,(,((((((((((*(,,,,,       ,           ,
                 ,,            ,             ,,,             ,            ,
                 ,                                            ,
                ,                                             ,
               ,                                              .,
                                                               ,

"""
  private val bannerColored =
    banner
      .colorize("\\(", Console.YELLOW + Console.YELLOW_B)
      .colorize("@", Console.WHITE_B)
      .colorize("&", Console.CYAN_B + Console.CYAN)
      .colorize(",", Console.MAGENTA)
}
