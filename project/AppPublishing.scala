import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.docker.DockerPlugin
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import sbt.AutoPlugin
import sbt.Plugins
import sbt.SettingKey
import sbt.Keys._
import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyKeys._

object AppPublishing extends AutoPlugin {
  override def trigger = noTrigger
  override def requires: Plugins =
    DockerPlugin && JavaAppPackaging && AssemblyPlugin

  override val projectSettings = Seq(
    assembly / assemblyJarName := "ankabot",
    assemblyPrependShellScript := Some(
      sbtassembly.AssemblyPlugin.defaultUniversalScript(shebang = false)
    ),
    maintainer := "mail@hnaderi.dev",
    executableScriptName := "ankabot",
    packageName := "ankabot",
    Docker / packageName := s"ankabot",
    dockerRepository := sys.env.get("DOCKER_REGISTRY"),
    dockerBaseImage := "eclipse-temurin:17-jre",
    dockerExposedPorts := Seq(8080),
    dockerExposedVolumes := Seq("/opt/docker/logs"),
    dockerUpdateLatest := true,
    Docker / daemonUserUid := Some("1001"),
    Docker / daemonUser := "ankabot",
    Docker / maintainer := "Hossein Naderi"
  )
}
