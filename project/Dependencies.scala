import sbt.*
import sbt.Keys.*

object Dependencies {
  object Versions {
    val auth0       = "4.5.0"
    val circe       = "0.14.14"
    val flywaydb    = "11.8.1"
    val langchain4j = "0.29.0"
    val logback     = "1.5.18"
    val minio       = "8.5.10"
    val mUnit       = "1.0.2"
    val postgresql  = "42.7.5"
    val quill       = "4.8.6"
    val slf4j       = "2.0.17"
    val sttp        = "4.0.12"
    val tapir       = "1.11.24"
    val zio         = "2.1.17"
    val zioConfig   = "4.0.2"
    val zioLogging  = "2.2.4"
    val zioPrelude  = "1.0.0-RC36"
    val gatling     = "3.14.7"
  }

  private val zioCoreDependencies = Seq(
    "dev.zio" %% "zio"         % Versions.zio,
    "dev.zio" %% "zio-streams" % Versions.zio
  )

  private val configDependencies = Seq(
    "dev.zio" %% "zio-config"          % Versions.zioConfig,
    "dev.zio" %% "zio-config-magnolia" % Versions.zioConfig,
    "dev.zio" %% "zio-config-typesafe" % Versions.zioConfig
  )

  private val databaseDependencies = Seq(
    "org.flywaydb"   % "flyway-core"                % Versions.flywaydb,
    "org.flywaydb"   % "flyway-database-postgresql" % Versions.flywaydb,
    "org.postgresql" % "postgresql"                 % Versions.postgresql
  )

  private val loggingDependencies = Seq(
    "dev.zio"       %% "zio-logging"       % Versions.zioLogging,
    "dev.zio"       %% "zio-logging-slf4j" % Versions.zioLogging,
    "ch.qos.logback" % "logback-classic"   % Versions.logback
  )

  private val quillDependencies = Seq(
    "io.getquill" %% "quill-jdbc-zio" % Versions.quill
  )

  private val redisDependencies = Seq(
    "redis.clients" % "jedis" % "5.1.3"
  )

  private val minioDependencies = Seq(
    "io.minio" % "minio" % Versions.minio
  )

  private val jwtDependencies = Seq(
    "com.auth0" % "java-jwt" % Versions.auth0
  )

  private val langchain4jDependencies = Seq(
    "dev.langchain4j" % "langchain4j" % Versions.langchain4j
  )

  val testingLibraryDependencies =
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit"        % Versions.mUnit % Test,
      "dev.zio"       %% "zio-test"     % Versions.zio   % Test,
      "dev.zio"       %% "zio-test-sbt" % Versions.zio   % Test,
      "org.scalamock" %% "scalamock"    % "6.0.0"        % Test,
      "io.gatling.highcharts" % "gatling-charts-highcharts" % Versions.gatling % Test,
      "io.gatling"            % "gatling-test-framework"    % Versions.gatling % Test

    )

  val domainLibraryDependencies: Setting[Seq[ModuleID]] =
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-prelude"          % Versions.zioPrelude,
      "dev.zio" %% "zio-prelude-magnolia" % Versions.zioPrelude
    )

  val applicationLibraryDependencies: Setting[Seq[ModuleID]] =
    libraryDependencies ++= zioCoreDependencies

  val infrastructureLibraryDependencies: Setting[Seq[ModuleID]] =
    libraryDependencies ++= (
      zioCoreDependencies ++
        configDependencies ++
        loggingDependencies ++
        jwtDependencies ++
        databaseDependencies ++
        quillDependencies ++
        redisDependencies ++
        minioDependencies ++
        langchain4jDependencies ++ Seq(
          "com.softwaremill.sttp.client4" %% "core"                     % Versions.sttp,
          "com.softwaremill.sttp.client4" %% "zio"                      % Versions.sttp,
          "io.circe"                      %% "circe-core"               % Versions.circe,
          "io.circe"                      %% "circe-generic"            % Versions.circe,
          "io.circe"                      %% "circe-parser"             % Versions.circe,
          "com.softwaremill.sttp.tapir"   %% "tapir-zio"                % Versions.tapir,
          "com.softwaremill.sttp.tapir"   %% "tapir-iron"               % Versions.tapir,
          "com.softwaremill.sttp.tapir"   %% "tapir-json-circe"         % Versions.tapir,
          "com.softwaremill.sttp.tapir"   %% "tapir-zio-http-server"    % Versions.tapir,
          "com.softwaremill.sttp.tapir"   %% "tapir-prometheus-metrics" % Versions.tapir,
          "com.softwaremill.sttp.tapir"   %% "tapir-swagger-ui-bundle"  % Versions.tapir,
          "com.softwaremill.sttp.tapir"   %% "tapir-sttp-stub-server"   % Versions.tapir % Test,
          "dev.zio"                       %% "zio-test"                 % Versions.zio   % Test,
          "dev.zio"                       %% "zio-test-junit"           % Versions.zio   % Test,
          "dev.zio"                       %% "zio-test-sbt"             % Versions.zio   % Test,
          "dev.zio"                       %% "zio-test-magnolia"        % Versions.zio   % Test
        )
    )
}
