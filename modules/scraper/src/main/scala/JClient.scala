package io.aibees.knowledgebase

import cats.effect.IO
import cats.effect.kernel.Resource
import org.http4s.client.Client
import org.http4s.jdkhttpclient.JdkHttpClient

import java.net.http.HttpClient
import scala.concurrent.duration.*
import scala.jdk.DurationConverters.*

object JClient {
  def apply(timeout: FiniteDuration = 5.seconds): Resource[IO, Client[IO]] =
    Resource.eval(IO {
      val builder = HttpClient
        .newBuilder()
        .connectTimeout(timeout.toJava)
        .version(HttpClient.Version.HTTP_2)

      // workaround for https://github.com/http4s/http4s-jdk-http-client/issues/200
      if (Runtime.version().feature() == 11) {
        val params =
          javax.net.ssl.SSLContext.getDefault().getDefaultSSLParameters()
        params.setProtocols(params.getProtocols().filter(_ != "TLSv1.3"))
        builder.sslParameters(params)
      }
      JdkHttpClient[IO](builder.build())
    })
}
