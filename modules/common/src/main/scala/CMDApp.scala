package dev.hnaderi.ankabot

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import com.monovore.decline.Command
import io.odin.Logger

abstract class CMDApp[T](cmd: Command[T]) extends IOApp {
  protected given logger: Logger[IO]

  override final def run(args: List[String]): IO[ExitCode] =
    IO.println(CMDApp.banner).as(cmd.parse(args, sys.env)).flatMap {
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
  private val banner = s"""
${Console.YELLOW}

                ,, ,,                     ,,,,,,,,,               ,
             ,,       ,,        ,,,*(((((((((,,,,,,.           ,,  ,,
          ,,             ,,,,,(((((((((((((((((((((,,      .,,       ,
       ,,               ,,,,*(((((((((((((((((((((((((,,,,            ,,
   ,,,                  ,,,(((((((*,((((((((((((/,/(((((,,              ,
               ,,,    ,,,*((((,*@@@@@@@,((((,,@@@@@@@,,(((,,,            ,,
              ,     ,,(*/((((,@@@@@,,&&&,*(,&@@@@#,&&&,,(((,,             .,
            ,,      ,  ,(((((,@@@@@,&&&&,*(,@@@@@,,&&&#,(((,,,    ,,,       ,,
          ,,          .,,,*((/,@@@@@@@@,,(((,(@@@@@@@(,((((,,,       ,       .,
         ,,             ,,,(((((/,,,,,(((((((((,,,,,(((((,,,,,        ,
       ,,           ,,,,,  ,(/((((((((((((,((((((((((((((,,            ,
      ,            ,,      ,,,*(((((((((((((((((((((((,,,,,,            ,
                  ,,          ,(,,,(,((((((((((*(,,,,,       ,           ,
                 ,,            ,             ,,,             ,            ,
                 ,                                            ,
                ,                                             ,
               ,                                              .,
                                                               ,

${Console.RESET}
"""
}
