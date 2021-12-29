package com.knoldus.accountant.impl

import akka.Done
import com.knoldus.accountant.api.DataModel.AccountantReport
import com.knoldus.accountant.impl.Accountant.InvoiceAdded
import slick.dbio.{DBIO, Effect}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape
import slick.sql.FixedSqlAction

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AccountantRepository(database: Database) {

  class AccountReportTable(tag: Tag) extends Table[AccountantReport](tag, "accountant_report_table"){

    def accountantId: Rep[String] = column[String]("id")
    def creationDate: Rep[Instant] = column[Instant]("creation_date")
    def invoiceId: Rep[String]    = column[String]("invoice_id",O.PrimaryKey)
    def invoiceAmount: Rep[Int]   = column[Int]("invoice_amount")
    def totalBill: Rep[Option[Int]] = column[Int]("total_bill")

    override def * :ProvenShape[AccountantReport] =
      (
        accountantId,
        creationDate,
        invoiceId,
        invoiceAmount,
        totalBill
      ).mapTo[AccountantReport]
  }

  val accountantReport = TableQuery[AccountReportTable]


  def createTable(): FixedSqlAction[Unit, NoStream, Effect.Schema] = accountantReport.schema.createIfNotExists

  def findById(id: String): Future[Option[AccountantReport]] =
    database.run(findByIdQuery(id))

  def createReport(accountantId: String, evt: InvoiceAdded): DBIO[Done] = {
    findByIdQuery(evt.invoiceId)
      .flatMap {
        case None => accountantReport += AccountantReport(accountantId, Instant.now(), evt.invoiceId, evt.withAmount, Some(evt.amountTillNow))
        case _    => DBIO.successful(Done)
      }
      .map(_ => Done)
      .transactionally
  }

  def addCheckoutTime(cartId: String, checkoutDate: Instant): DBIO[Done] = {
    DBIO.successful(Done)
  }

  private def findByIdQuery(invoiceId: String): DBIO[Option[AccountantReport]] =
    accountantReport
      .filter(_.invoiceId === invoiceId)
      .result
      .headOption

}
