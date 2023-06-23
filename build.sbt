inThisBuild(
  Seq(
    tlBaseVersion := "0.0",
    organization := "dev.hnaderi",
    organizationName := "Hossein Naderi",
    licenses := Seq(License.Apache2),
    startYear := Some(2023),
    developers := List(
      Developer(
        "hnaderi",
        "Hossein Naderi",
        "mail@hnaderi.dev",
        url("https://hnaderi.dev")
      )
    ),
    fork := true,
    scalaVersion := "3.3.0",
    resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
    scalacOptions += "-Wunused:all"
  )
)

lazy val root = project
  .in(file("."))
  .aggregate(common, scraper, extractor, cli, worker, files)

def module(mname: String): Project => Project =
  _.in(file(s"modules/$mname"))
    .settings(
      name := s"module-$mname",
      moduleName := s"ankabot-$mname",
      libraryDependencies ++= Dependencies.test
    )

lazy val common = module("common") {
  project
    .settings(
      libraryDependencies ++=
        Dependencies.effect ++
          Dependencies.circe ++
          Dependencies.fs2CSV ++
          Dependencies.decline ++
          Dependencies.odin
    )
}

lazy val files = module("files") {
  project
    .dependsOn(common)
    .settings(
      libraryDependencies ++= Dependencies.effect ++ Dependencies.fs2CSV ++ Dependencies.circe ++ Dependencies.s3
    )
}

lazy val jsoup = module("jsoup") {
  project
    .dependsOn(common)
    .settings(
      libraryDependencies ++= Dependencies.jsoup
    )
}

lazy val scraper = module("scraper") {
  project
    .dependsOn(common, jsoup)
    .settings(
      libraryDependencies ++= Dependencies.emberClient ++ Dependencies.jdkClient
    )
}

lazy val extractor = module("extractor") {
  project.dependsOn(common, jsoup)
}

lazy val worker = module("worker") {
  project
    .dependsOn(scraper, extractor)
    .settings(
      libraryDependencies ++=
        Dependencies.skunk ++ Dependencies.lepus ++ Dependencies.emberServer
    )
}

lazy val cli = module("cli") {
  project
    .dependsOn(scraper, extractor, worker, files)
    .enablePlugins(AppPublishing)
    .enablePlugins(K8sDeployment)
    .settings(
      ankabotNodeSelector := sys.env.get("ANKABOT_NODES").flatMap { s =>
        val pattern = "(.*)=(.*)".r

        val out = s.linesIterator.collect { case pattern(k, v) => (k, v) }.toMap

        if (out.isEmpty) None else Some(out)
      },
      ankabotNamespace := sys.env.get("ANKABOT_NAMESPACE")
    )
}

addCommandAlias("fmt", "scalafmtAll;scalafmtSbt")
