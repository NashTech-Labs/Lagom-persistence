package com.knoldus.accountant.api

import play.api.libs.json.{Format, Json}

import java.time.Instant

object DataModel {

  case class Invoice(id: String, amount: Int)

  case class Invoices(invoice: Seq[Invoice])

  case class AccountantView(id: String, invoices: Seq[Invoice], billGenerated : Boolean)

  case class AccountantList(id: String, invoices: Seq[Invoice], totalBillGenerated: Boolean, totalBill: Option[Int] = None)

  case class AccountantReport(id: String, creationDate: Instant,invoiceId: String, invoiceAmount: Int, totalBill: Option[Int])

  object AccountantView{
    implicit val format: Format[AccountantView] = Json.format[AccountantView]
  }

  object AccountantReport{
    implicit val format: Format[AccountantReport] = Json.format[AccountantReport]


    def tupled(t: (String, Instant, String, Int, Option[Int])) = AccountantReport(t._1, t._2, t._3, t._4, t._5)
  }

  object AccountantList{
    implicit val format: Format[AccountantList] = Json.format[AccountantList]
  }

  object Invoice{
    implicit val format: Format[Invoice] = Json.format[Invoice]
  }

  object Invoices{
    implicit val format: Format[Invoices] = Json.format[Invoices]
  }

}
