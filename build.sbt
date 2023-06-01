ThisBuild / tlBaseVersion := "0.0"
ThisBuild / organization := "dev.hnaderi"
ThisBuild / organizationName := "Hossein Naderi"
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
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))
ThisBuild / tlCiMimaBinaryIssueCheck := false

lazy val root = project
  .in(file("."))
  .aggregate(common, scraper, extractor, cli, worker)

def module(mname: String): Project => Project =
  _.in(file(s"modules/$mname"))
    .settings(
      name := s"module-$mname",
      moduleName := s"ankabot-$mname",
      libraryDependencies ++= Seq(
        "org.scalameta" %% "munit" % "0.7.29" % Test,
        "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
      )
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
        "org.gnieh" %% "fs2-data-csv" % "1.7.0",
        "com.github.valskalla" %% "odin-slf4j" % "0.13.0",
        "com.monovore" %% "decline" % "2.4.1"
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
        "org.http4s" %% "http4s-ember-client" % "0.23.19",
        "org.http4s" %% "http4s-jdk-http-client" % "0.9.1-5-999d1cd-SNAPSHOT"
      )
    )
}

lazy val extractor = module("extractor") {
  project.dependsOn(common, jsoup)
}

lazy val worker = module("worker") {
  project
    .dependsOn(scraper, extractor)
    .settings(
      libraryDependencies ++= Seq(
        "org.tpolecat" %% "skunk-core" % "0.5.1",
        "dev.hnaderi" %% "lepus-std" % "0.3.0",
        "org.http4s" %% "http4s-dsl" % "0.23.19",
        "org.http4s" %% "http4s-ember-server" % "0.23.19"
      )
    )
}

lazy val cli = module("cli") {
  project
    .dependsOn(scraper, extractor, worker)
    .settings(
      assembly / assemblyJarName := s"kb",
      assemblyPrependShellScript := Some(
        sbtassembly.AssemblyPlugin.defaultUniversalScript(shebang = false)
      ),
      maintainer := "mail@hnaderi.dev",
      executableScriptName := "kb",
      packageName := "kb",
      Docker / packageName := s"kb-cli",
      dockerRepository := sys.env.get("DOCKER_REGISTRY"),
      dockerBaseImage := "openjdk:11-jre-slim",
      dockerExposedPorts := Seq(8080),
      dockerExposedVolumes := Seq("/opt/docker/logs"),
      dockerUpdateLatest := true,
      Docker / daemonUserUid := Some("1001"),
      Docker / daemonUser := "ankabot",
      Docker / maintainer := "Hossein Naderi"
    )
    .enablePlugins(JavaAppPackaging)
    .enablePlugins(DockerPlugin)
}

addCommandAlias("fmt", "scalafmtAll;scalafmtSbt")
