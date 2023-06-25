import sbt._

object Dependencies {
  val effect = Seq(
    "org.typelevel" %% "cats-core" % "2.9.0",
    "org.typelevel" %% "cats-effect" % "3.5.0",
    "co.fs2" %% "fs2-io" % "3.7.0"
  )
  val circe = Seq(
    "io.circe" %% "circe-generic" % "0.14.5",
    "io.circe" %% "circe-parser" % "0.14.5"
  )
  val fs2CSV = Seq("org.gnieh" %% "fs2-data-csv" % "1.7.1")

  val decline = Seq("com.monovore" %% "decline" % "2.4.1")
  val odin = Seq("com.github.valskalla" %% "odin-slf4j" % "0.13.0")

  val s3 = Seq("io.laserdisc" %% "fs2-aws-s3" % "6.0.1")

  val jsoup = Seq("org.jsoup" % "jsoup" % "1.15.4")

  val emberClient = Seq("org.http4s" %% "http4s-ember-client" % "0.23.21")
  val emberServer = Seq(
    "org.http4s" %% "http4s-dsl" % "0.23.21",
    "org.http4s" %% "http4s-ember-server" % "0.23.21"
  )
  val jdkClient = Seq(
    "org.http4s" %% "http4s-jdk-http-client" % "0.9.1-5-999d1cd-SNAPSHOT"
  )

  val lepus = Seq("dev.hnaderi" %% "lepus-std" % "0.4.0")
  val skunk = Seq("org.tpolecat" %% "skunk-core" % "0.6.0")

  val test = Seq(
    "org.scalameta" %% "munit" % "0.7.29" % Test,
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
  )
}
