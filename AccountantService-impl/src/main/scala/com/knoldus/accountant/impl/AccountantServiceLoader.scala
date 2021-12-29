package com.knoldus.accountant.impl

import akka.cluster.sharding.typed.scaladsl.Entity
import com.knoldus.accountant.api.AccountatService
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.api.{Descriptor, ServiceLocator}
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.BodyParsers.Default

import scala.concurrent.ExecutionContext



class HelloServiceLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication = {
    new HelloServiceApplication(context) with AkkaDiscoveryComponents
  }
  override def loadDevMode(context: LagomApplicationContext): LagomApplication = {
    new HelloServiceApplication(context)  {
      override def serviceLocator: ServiceLocator = ServiceLocator.NoServiceLocator
    }
  }
  override def describeService: Option[Descriptor] = Some(readDescriptor[AccountatService])

}

abstract class HelloServiceApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
  with HelloServiceComponents {


  override lazy val lagomServer: LagomServer = serverFor[AccountatService](wire[AccountantServiceImpl])

  lazy val bodyParserDefault: Default = wire[Default]
}

trait HelloServiceComponents
  extends LagomServerComponents
    with SlickPersistenceComponents
    with HikariCPComponents
    with AhcWSComponents {


  implicit def executionContext: ExecutionContext

  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = AccountantSerializerRegistry

  lazy val reportRepository: AccountantRepository =
    wire[AccountantRepository]
  readSide.register(wire[AccountProcessor])

  // Initialize the sharding for the ShoppingCart aggregate.
  // See https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html
  clusterSharding.init(
    Entity(Accountant.typeKey) { entityContext =>
      Accountant(entityContext)
    }
  )
}