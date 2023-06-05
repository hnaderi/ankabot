import sbt.Keys._
import sbt.AutoPlugin
import sbt.Plugins

import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._

import dev.hnaderi.k8s.KObject
import dev.hnaderi.k8s.manifest._
import dev.hnaderi.sbtk8s.K8sManifestPlugin
import dev.hnaderi.sbtk8s.K8sManifestPlugin.autoImport._
import dev.hnaderi.k8s.implicits._
import dev.hnaderi.k8s.Labels

import io.k8s.api.apps.v1.DaemonSet
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import io.k8s.api.apps.v1.DaemonSetSpec
import io.k8s.apimachinery.pkg.apis.meta.v1.LabelSelector
import io.k8s.api.core.v1.PodTemplateSpec
import io.k8s.api.core.v1.PodSpec
import io.k8s.api.core.v1.Container
import io.k8s.api.core.v1.LocalObjectReference
import io.k8s.api.core.v1.ContainerPort
import io.k8s.api.core.v1.EnvFromSource

object K8sDeployment extends AutoPlugin {
  override def trigger = noTrigger
  override def requires: Plugins = K8sManifestPlugin

  private val envSource = Seq(
    EnvFromSource()
  )

  override val projectSettings = Seq(
    k8sManifestObjects := Seq(
      DaemonSet(
        metadata = ObjectMeta(
          name = "worker",
          namespace = "ankabot",
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
              imagePullSecrets = Seq(LocalObjectReference(name = "")),
              containers = Seq(
                Container(
                  name = "worker",
                  image = (Docker / dockerAlias).value.toString,
                  command = Seq("service", "start", "-l", "8080"),
                  ports = Seq(ContainerPort(8080, name = "api")),
                  envFrom = envSource
                )
              ),
              initContainers = Seq(
                Container(
                  name = "worker",
                  image = (Docker / dockerAlias).value.toString,
                  command = Seq("service", "start", "migrate"),
                  envFrom = envSource
                )
              ),
              nodeSelector = Map("" -> "")
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
