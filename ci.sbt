import org.typelevel.sbt.gha.JobEnvironment
inThisBuild(
  Seq(
    githubWorkflowEnv ++= Map(
      "DOCKER_REGISTRY" -> s"ghcr.io/$${{ github.repository_owner }}"
    ),
    githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17")),
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
    )
  )
)
