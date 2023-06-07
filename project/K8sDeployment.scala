import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.docker.DockerPlugin
import dev.hnaderi.k8s.KObject
import dev.hnaderi.k8s.Labels
import dev.hnaderi.k8s.implicits._
import dev.hnaderi.k8s.manifest._
import dev.hnaderi.sbtk8s.K8sManifestPlugin
import dev.hnaderi.sbtk8s.K8sManifestPlugin.autoImport._
import io.k8s.api.apps.v1.DaemonSet
import io.k8s.api.apps.v1.DaemonSetSpec
import io.k8s.api.core.v1.Container
import io.k8s.api.core.v1.ContainerPort
import io.k8s.api.core.v1.EnvFromSource
import io.k8s.api.core.v1.LocalObjectReference
import io.k8s.api.core.v1.PodSpec
import io.k8s.api.core.v1.PodTemplateSpec
import io.k8s.api.core.v1.SecretEnvSource
import io.k8s.apimachinery.pkg.apis.meta.v1.LabelSelector
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import sbt.AutoPlugin
import sbt.Keys._
import sbt.Plugins
import sbt.SettingKey
import io.k8s.api.core.v1.Service
import io.k8s.api.core.v1.ServiceSpec
import io.k8s.api.core.v1.ServicePort

object K8sDeployment extends AutoPlugin {
  override def trigger = noTrigger
  override def requires: Plugins = K8sManifestPlugin && DockerPlugin

  object autoImport {
    val ankabotEnvSource: SettingKey[Seq[EnvFromSource]] = SettingKey(
      "environment sources for ankabot"
    )
    val ankabotNodeSelector: SettingKey[Option[Map[String, String]]] =
      SettingKey("node selector for ankabot")
    val ankabotNamespace: SettingKey[Option[String]] = SettingKey(
      "namespace to deploy ankabot"
    )
  }

  import autoImport._

  override val projectSettings = Seq(
    ankabotEnvSource := Seq(
      EnvFromSource(secretRef =
        SecretEnvSource(name = "credentials", optional = false)
      )
    ),
    ankabotNodeSelector := None,
    ankabotNamespace := None,
    k8sManifestObjects := Seq(
      Service(
        metadata = ObjectMeta(
          name = "api",
          namespace = ankabotNamespace.value,
          labels = Map(
            Labels.name("ankabot-api"),
            Labels.version(version.value)
          )
        ),
        spec = ServiceSpec(
          ports = Seq(ServicePort(80, name = "api")),
          selector = Map(
            Labels.name("ankabot-worker"),
            Labels.component("worker")
          )
        )
      ),
      DaemonSet(
        metadata = ObjectMeta(
          name = "worker",
          namespace = ankabotNamespace.value,
          labels = Map(
            Labels.name("ankabot-worker"),
            Labels.version(version.value),
            Labels.component("worker")
          )
        ),
        spec = DaemonSetSpec(
          template = PodTemplateSpec(
            metadata = ObjectMeta(
              name = "worker",
              labels = Map(
                Labels.name("ankabot-worker"),
                Labels.version(version.value),
                Labels.component("worker")
              )
            ),
            spec = PodSpec(
              containers = Seq(
                Container(
                  name = "worker",
                  image = (Docker / dockerAlias).value.toString,
                  args = Seq("service", "start", "-l", "8080"),
                  ports = Seq(ContainerPort(8080, name = "api")),
                  envFrom = ankabotEnvSource.value
                )
              ),
              initContainers = Seq(
                Container(
                  name = "migration",
                  image = (Docker / dockerAlias).value.toString,
                  args = Seq("service", "migrate"),
                  envFrom = ankabotEnvSource.value
                )
              ),
              nodeSelector = ankabotNodeSelector.value
            )
          ),
          selector = LabelSelector(matchLabels =
            Map(
              Labels.name("ankabot-worker")
            )
          )
        )
      )
    )
  )
}
