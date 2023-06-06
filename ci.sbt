import org.typelevel.sbt.gha.JobEnvironment
inThisBuild(
  Seq(
    githubWorkflowEnv ++= Map(
      "DOCKER_REGISTRY" -> s"ghcr.io/$${{ github.repository_owner }}",
      "ANKABOT_NODES" -> s"$${{ secrets.ANKABOT_NODES }}",
      "ANKABOT_NAMESPACE" -> s"$${{ secrets.ANKABOT_NAMESPACE }}"
    ),
    githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11")),
    tlCiMimaBinaryIssueCheck := false,
    githubWorkflowPublish := Seq(WorkflowStep.Sbt(List("cli/Docker/publish"))),
    githubWorkflowPublishTargetBranches := Seq(
      RefPredicate.Equals(Ref.Branch("main")),
      RefPredicate.StartsWith(Ref.Tag("v"))
    ),
    githubWorkflowPublishPreamble += WorkflowStep.Use(
      UseRef.Public("docker", "login-action", "v2"),
      params = Map(
        "registry" -> "ghcr.io",
        "username" -> s"$${{ github.repository_owner }}",
        "password" -> s"$${{ secrets.GITHUB_TOKEN }}"
      ),
      name = Some("Login to github container registery")
    ),
    githubWorkflowPublishPostamble ++= Seq(
      WorkflowStep
        .Sbt(List("k8sManifestGen"), name = Some("Generate k8s manifest")),
      WorkflowStep.Use(
        UseRef.Public("actions", "upload-artifact", "v3"),
        name = Some("upload manifest"),
        params = Map(
          "name" -> "manifest",
          "path" -> "modules/cli/target/k8s/manifest.yml"
        )
      )
    ),
    githubWorkflowGeneratedCI += WorkflowJob(
      id = "deploy",
      name = "Deploy",
      cond = githubWorkflowGeneratedCI.value
        .find(_.id == "publish")
        .flatMap(_.cond),
      steps = List(
        WorkflowStep.Use(
          UseRef.Public("actions", "download-artifact", "v3"),
          name = Some("upload manifest"),
          params = Map("name" -> "manifest")
        ),
        WorkflowStep.Use(
          UseRef.Public("azure", "setup-kubectl", "v3"),
          id = Some("install"),
          name = Some("Setup kubectl")
        ),
        WorkflowStep.Use(
          UseRef.Public("azure", "k8s-set-context", "v3"),
          id = Some("configure"),
          name = Some("Set context"),
          params = Map(
            "method" -> "service-account",
            "k8s-url" -> s"$${{ secrets.K8S_URL }}",
            "k8s-secret" -> s"$${{ secrets.SA_SECRET }}"
          )
        ),
        WorkflowStep.Run(
          id = Some("deploy"),
          name = Some("Deploy to cluster"),
          commands = List(
            "kubectl apply -f manifest.yml"
          )
        )
      ),
      environment = Some(JobEnvironment("production")),
      needs = List("publish")
    )
  )
)
