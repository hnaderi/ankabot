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

lazy val root = project.in(file(".")).aggregate(common, scraper, extractor)

def module(mname: String): Project => Project =
  _.in(file(s"modules/$mname"))
    .settings(
      name := s"module-$mname",
      moduleName := s"knowledge-base-$mname",
      libraryDependencies ++= Seq(
        "org.scalameta" %% "munit" % "0.7.29" % Test,
        "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
      ),
      assembly / assemblyJarName := s"$mname.jar"
    )

lazy val common = module("common") {
  project
    .settings(
      libraryDependencies ++= Seq(
        "org.typelevel" %% "cats-core" % "2.9.0",
        "org.typelevel" %% "cats-effect" % "3.4.9",
        "co.fs2" %% "fs2-io" % "3.6.1",
        "io.circe" %% "circe-generic" % "0.14.5",
        "io.circe" %% "circe-parser" % "0.14.5",
        "com.github.valskalla" %% "odin-slf4j" % "0.13.0"
      )
    )
}

lazy val jsoup = module("jsoup") {
  project
    .dependsOn(common)
    .settings(
      libraryDependencies ++= Seq(
        "org.jsoup" % "jsoup" % "1.15.4"
      )
    )
}

lazy val scraper = module("scraper") {
  project
    .dependsOn(common, jsoup)
    .settings(
      libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-netty-client" % "0.5.6",
        "org.http4s" %% "http4s-ember-client" % "0.23.18"
      ),
      assembly / assemblyMergeStrategy := {
        case PathList("META-INF", "io.netty.versions.properties") =>
          MergeStrategy.first
        case x =>
          val oldStrategy = (assembly / assemblyMergeStrategy).value
          oldStrategy(x)
      }
    )
}

lazy val extractor = module("extractor") {
  project.dependsOn(common, jsoup)
}
