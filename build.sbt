
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.7"

lazy val root = (project in file("."))
  .aggregate(`AccountantService-api`, `AccountantService-impl` )
  .settings(
    name := "Lagom-persistence"
  )


val macwire               = "com.softwaremill.macwire"  %%  "macros"      % "2.3.3" % "provided"
val scalaTest             = "org.scalatest"             %%  "scalatest"   % "3.1.1" % Test
val postgresDriver        = "org.postgresql"            %   "postgresql"  % "42.2.22"


lazy val `AccountantService-api` = (project in file("AccountantService-api"))
  .settings(
    ThisBuild / lagomKafkaEnabled := false,
    ThisBuild / lagomCassandraEnabled := false,
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `AccountantService-impl` = (project in file("AccountantService-impl"))
  .enablePlugins(LagomScala)
  .settings(

    ThisBuild / lagomKafkaEnabled := false,
    ThisBuild / lagomCassandraEnabled := false,
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceJdbc,
      lagomScaladslKafkaBroker,
      lagomScaladslAkkaDiscovery,
      jdbc,
      postgresDriver,
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`AccountantService-api`)
