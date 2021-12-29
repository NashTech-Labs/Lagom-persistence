package com.knoldus.accountant.impl

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import com.knoldus.accountant.api.{AccountatService, DataModel}
import com.knoldus.accountant.api.DataModel.{AccountantView, Invoice}
import com.knoldus.accountant.impl.Accountant.{Accepted, AddInvoice, Command, Rejected}
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.BadRequest

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class AccountantServiceImpl(
                        clusterSharding: ClusterSharding
                      )(implicit ec: ExecutionContext)
  extends AccountatService {

  private def entityRef(id: String): EntityRef[Command] =
    clusterSharding.entityRefFor(Accountant.typeKey, id)

  implicit val timeout: Timeout = Timeout(5.seconds)


  override def sayHello(userName: String): ServiceCall[NotUsed, String] = ServiceCall{
    _ =>
      Future.successful {
        s"Hello! $userName from Lagom service"
      }
  }


  override def addInvoices(id: String): ServiceCall[DataModel.Invoices, AccountantView] = ServiceCall{
    invoices =>
      val a = invoices.invoice.map{
        x =>
          entityRef(id)
            .ask(reply => AddInvoice(x.id, x.amount, reply))
            .map {
              case Accepted(invoice, amount) => Invoice(invoice, amount)
              case Rejected(reason) => throw BadRequest(reason)
            }
  }
      val x = Future.sequence(a)
      x.map{
        invoices =>
          AccountantView(
            id,
            invoices,
            billGenerated = false
          )
      }
  }

  override def getReport(id: String): ServiceCall[NotUsed, DataModel.AccountantReport] = ServiceCall {
    _ =>
      Future.successful {
        throw BadRequest("Service is not running, comeback later")
      }
  }

  override def get(id: String): ServiceCall[NotUsed, DataModel.AccountantList] = ServiceCall {
    _ =>
      Future.successful {
        throw BadRequest("Service is not running, comeback later")
      }
  }
}

