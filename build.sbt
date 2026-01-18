import Dependencies.{
  applicationLibraryDependencies,
  domainLibraryDependencies,
  infrastructureLibraryDependencies,
  testingLibraryDependencies
}

val scala3 = "3.7.3"

name := "com.cyrelis.srag"

inThisBuild(
  List(
    scalaVersion := scala3,
    dependencyOverrides ++= Seq(
      "org.scala-lang" %% "scala3-library" % scala3,
      "dev.zio"        %% "zio-json"       % "0.7.40",
      "io.suzaku"      %% "boopickle"      % "1.5.0",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("io.suzaku", "boopickle_2.13"),
      ExclusionRule("com.typesafe.scala-logging", "scala-logging_2.13")
    ),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-Wunused:all"
//      "-Xfatal-warnings"
    ),
    run / fork := true,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    // Help Metals index Java libraries by ensuring proper classpath export
    exportJars := true
  )
)

lazy val root = project
  .in(file("."))
  .aggregate(
    `srag-domain`,
    `srag-application`,
    `srag-infrastructure`
  )
  .disablePlugins(RevolverPlugin)
  .settings(
    publish / skip := true
  )

lazy val `srag-domain` = project
  .in(file("srag-domain"))
  .disablePlugins(RevolverPlugin)
  .settings(
    domainLibraryDependencies,
    publish / skip := true
  )

lazy val `srag-application` = project
  .in(file("srag-application"))
  .disablePlugins(RevolverPlugin)
  .dependsOn(`srag-domain`)
  .settings(
    applicationLibraryDependencies,
    publish / skip := true
  )

lazy val `srag-infrastructure` = project
  .in(file("srag-infrastructure"))
  .enablePlugins(JavaAppPackaging, DockerPlugin, AshScriptPlugin, RevolverPlugin, GatlingPlugin)
  .dependsOn(`srag-application`)
  .settings(
    fork      := true,
    mainClass := Some("com.cyrelis.srag.infrastructure.Main"),
    infrastructureLibraryDependencies,
    testingLibraryDependencies,
    publish / skip := true
  )
  .settings(dockerSettings)
  .settings(assemblySettings)

Test / fork := false

lazy val dockerSettings = {
  import DockerPlugin.autoImport._
  import DockerPlugin.globalSettings._
  import sbt.Keys._
  Seq(
    Docker / maintainer     := "Cyril Deschamps",
    Docker / dockerUsername := Some("cyril-deschamps"),
    Docker / packageName    := "srag",
    dockerBaseImage         := "azul/zulu-openjdk-alpine:21-jre-headless",
    dockerRepository        := Some("ghcr.io"),
    dockerUpdateLatest      := true,
    dockerExposedPorts      := Seq(8000),
    // Use a simple tag format without version to avoid invalid characters
    Docker / version := "latest"
  )
}

lazy val assemblySettings = {
  import sbtassembly.AssemblyPlugin.autoImport._
  import sbtassembly.MergeStrategy
  Seq(
    assembly / assemblyMergeStrategy := {
      case PathList("deriving.conf")                                  => MergeStrategy.concat
      case PathList("scala", "annotation", "unroll.tasty")            => MergeStrategy.first
      case PathList("scala", "annotation", "unroll.class")            => MergeStrategy.first
      case PathList("module-info.class")                              => MergeStrategy.discard
      case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard
      case PathList("META-INF", "io.netty.versions.properties")       => MergeStrategy.first
      case x if x.contains("io/getquill/")                            => MergeStrategy.first
      case x                                                          =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    // Exclude unnecessary files to reduce JAR size
    assembly / assemblyExcludedJars := {
      val cp = (assembly / fullClasspath).value
      cp.filter { file =>
        val name = file.data.getName
        name.contains("scala-compiler") ||
        name.contains("scala-reflect") ||
        name.contains("scalap") ||
        name.contains("test") ||
        name.contains("junit") ||
        name.contains("scalatest") ||
        name.contains("munit")
      }
    }
  )
}
