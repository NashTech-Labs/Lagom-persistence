package com.knoldus.accountant.api

import akka.NotUsed
import com.knoldus.accountant.api.DataModel._
import com.lightbend.lagom.scaladsl.api.transport.Method.{GET, POST}
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}


trait AccountatService extends Service{

  def sayHello(userName: String) : ServiceCall[NotUsed,String]

  def addInvoices(id: String): ServiceCall[Invoices, AccountantView]

  def getReport(id: String): ServiceCall[NotUsed, AccountantReport]

  def get(id: String): ServiceCall[NotUsed, AccountantList]

  override def descriptor: Descriptor = {
    import Service._
    named("HelloService")
      .withCalls(
        restCall(GET,"/api/hello/:name", sayHello _),
        restCall(GET, "/api/accountant/:id", get _),
        restCall(GET, "/api/accountant/:id/report", getReport _),
        restCall(POST, "/api/accountant/:id/addInvoices", addInvoices _)
      )
      .withAutoAcl(true)
  }
}
