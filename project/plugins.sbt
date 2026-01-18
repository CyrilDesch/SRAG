// scalafmt: { maxColumn = 120, style = defaultWithAlign }
addSbtPlugin("org.scalameta"  % "sbt-scalafmt"        % "2.5.5")
addSbtPlugin("com.github.sbt" % "sbt-ci-release"      % "1.11.2")
addSbtPlugin("com.eed3si9n"   % "sbt-assembly"        % "2.3.1")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")
addSbtPlugin("com.github.sbt" % "sbt-dynver"          % "5.1.1")
addSbtPlugin("com.github.sbt" % "sbt-unidoc"          % "0.6.0")
addSbtPlugin("com.github.sbt" % "sbt-ghpages"         % "0.9.0")
// will reStart server on code modification.
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")
// Giter8 support
addSbtPlugin("org.foundweekends.giter8" % "sbt-giter8-scaffold" % "0.18.0")
// Scalafix
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.3")
// Bloop for Metals support
addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.5.15")
// Gatling plugin for performance testing
addSbtPlugin("io.gatling" % "gatling-sbt" % "4.17.8")

