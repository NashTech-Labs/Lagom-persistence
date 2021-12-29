package com.knoldus.accountant.impl

import akka.Done
import com.knoldus.accountant.impl.Accountant.{Event, InvoiceAdded, InvoiceRemoved}
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, ReadSideProcessor}
import slick.dbio.DBIOAction

class AccountProcessor(readSide: SlickReadSide, repository: AccountantRepository)
  extends ReadSideProcessor[Event] {

  override def buildHandler(): ReadSideProcessor.ReadSideHandler[Event] =
    readSide
      .builder[Event]("shopping-cart-report")
      .setGlobalPrepare(repository.createTable())
      .setEventHandler[InvoiceAdded] { envelope =>
        repository.createReport(envelope.entityId, envelope.event)
      }
      .setEventHandler[InvoiceRemoved] { envelope =>
        DBIOAction.successful(Done) // not used in report
      }
      .build()

  override def aggregateTags: Set[AggregateEventTag[Event]] = Event.Tag.allTags
}
