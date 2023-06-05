inThisBuild(
  Seq(
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
    githubWorkflowEnv += "DOCKER_REGISTRY" -> s"ghcr.io/$${{ github.repository_owner }}"
  )
)
