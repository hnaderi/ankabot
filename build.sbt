ThisBuild / tlBaseVersion := "0.0"
ThisBuild / organization := "io.aibees"
ThisBuild / organizationName := "AI Bees"
ThisBuild / startYear := Some(2023)
ThisBuild / developers := List(
  Developer(
    "hnaderi",
    "Hossein Naderi",
    "mail@hnaderi.dev",
    url("https://hnaderi.dev")
  )
)
ThisBuild / fork := true
ThisBuild / scalaVersion := "3.2.2"

lazy val root = project.in(file(".")).aggregate(core, scraper, extractor)

def module(mname: String): Project => Project =
  _.in(file(s"modules/$mname"))
    .settings(
      name := s"module-$mname",
      moduleName := s"knowledge-base-$mname"
    )

lazy val core = project
  .in(file("core"))
  .settings(
    name := "knowledge-base",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.9.0",
      "org.typelevel" %% "cats-effect" % "3.4.9",
      "org.http4s" %% "http4s-jdk-http-client" % "0.9.0",
      "io.circe" %% "circe-generic" % "0.14.5",
      "io.circe" %% "circe-parser" % "0.14.5",
      "com.github.valskalla" %% "odin-slf4j" % "0.13.0",
      "org.jsoup" % "jsoup" % "1.15.4",
      "org.scalameta" %% "munit" % "0.7.29" % Test,
      "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
    )
  )

lazy val scraper = module("scraper") {
  project.dependsOn(core)
}

lazy val extractor = module("extractor") {
  project.dependsOn(core)
}
