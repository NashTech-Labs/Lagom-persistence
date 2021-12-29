package com.knoldus.accountant.impl

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{EntityContext, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect.reply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import com.lightbend.lagom.scaladsl.persistence._
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json.{Format, JsResult, JsValue, Json}
import java.time.Instant
import scala.collection.immutable
import scala.collection.immutable.Seq

object Accountant {

  trait CommandSerializable

  sealed trait Command extends CommandSerializable

  final case class AddInvoice(invoiceId: String, amount: Int, replyTo: ActorRef[Confirmation]) extends Command

  final case class RemoveInvoice(invoiceId: String, amount: Int, replyTo: ActorRef[Confirmation]) extends Command

  final case class AdjustInvoiceAmount(invoiceId: String, amount: Int, replyTo: ActorRef[Confirmation]) extends Command

  final case class Result(replyTo: ActorRef[Confirmation]) extends Command

  final case class Get(replyTo: ActorRef[Summary]) extends Command

  final case class Summary(invoices: Map[String, Int])

  sealed trait Confirmation

  final case class Accepted(invoiceId: String, amount: Int) extends Confirmation

  final case class Rejected(reason: String) extends Confirmation


  implicit val summaryFormat: Format[Summary]               = Json.format
  implicit val confirmationAcceptedFormat: Format[Accepted] = Json.format
  implicit val confirmationRejectedFormat: Format[Rejected] = Json.format
  implicit val confirmationFormat: Format[Confirmation] = new Format[Confirmation] {
    override def reads(json: JsValue): JsResult[Confirmation] = {
      if ((json \ "reason").isDefined)
        Json.fromJson[Rejected](json)
      else
        Json.fromJson[Accepted](json)
    }

    override def writes(o: Confirmation): JsValue = {
      o match {
        case acc: Accepted => Json.toJson(acc)
        case rej: Rejected => Json.toJson(rej)
      }
    }
  }

  sealed trait Event extends AggregateEvent[Event] {
    override def aggregateTag: AggregateEventTagger[Event] = Event.Tag
  }

  object Event {
    val Tag: AggregateEventShards[Event] = AggregateEventTag.sharded[Event](numShards = 10)
  }


  final case class InvoiceAdded(invoiceId: String, withAmount: Int, amountTillNow: Int = 0) extends Event

  final case class InvoiceRemoved(invoiceId: String) extends Event

  final case class InvoiceAmountAdjusted(invoiceId: String, newAmount: Int) extends Event

  final case class ResultGenerated(eventTime: Instant) extends Event

  // Events get stored and loaded from the database, hence a JSON format
  //  needs to be declared so that they can be serialized and deserialized.
  implicit val invoiceAddedFormat: Format[InvoiceAdded]                       = Json.format
  implicit val invoiceRemovedFormat: Format[InvoiceRemoved]                   = Json.format
  implicit val invoiceAmountAdjustedFormat: Format[InvoiceAmountAdjusted]     = Json.format
  implicit val resultGenerated: Format[ResultGenerated]                       = Json.format

  val empty: Accountant = Accountant(invoices = Map.empty)

  val typeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Accountant")

  // We can then access the entity behavior in our test tests, without the need to tag
  // or retain events.
  def apply(persistenceId: PersistenceId): EventSourcedBehavior[Command, Event, Accountant] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, Accountant](
        persistenceId = persistenceId,
        emptyState = Accountant.empty,
        commandHandler = (accountant, cmd) => accountant.applyCommand(cmd),
        eventHandler = (accountant, evt) => accountant.applyEvent(evt)
      )
  }

  def apply(entityContext: EntityContext[Command]): Behavior[Command] =
    apply(PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
      .withTagger(AkkaTaggerAdapter.fromLagom(entityContext, Event.Tag))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))


  implicit val accountantFormat: Format[Accountant] = Json.format
}


final case class Accountant(invoices: Map[String, Int], totalAmount: Int = 0) {

  import Accountant._

  def applyCommand(cmd: Command): ReplyEffect[Event, Accountant] = {

    cmd match {
      case AddInvoice(invoiceId, amount, replyTo)           => onAddInvoice(invoiceId, amount, replyTo)
      case RemoveInvoice(invoiceId, amount, replyTo)        => onRemoveInvoice(invoiceId, amount, replyTo)
      case AdjustInvoiceAmount(invoiceId, amount, replyTo)  => onAdjustInvoiceAmount(invoiceId, amount, replyTo)
      case Get(replyTo)                                     => onGet(replyTo)
      case Result(replyTo)                                  => onResultGenerated(replyTo)
    }

  }

  private def onAddInvoice(
                          invoiceId: String,
                          amount: Int,
                          replyTo: ActorRef[Confirmation]
                          ): ReplyEffect[Event, Accountant] = {
    if (invoices.contains(invoiceId))
      Effect.reply(replyTo)(Rejected(s"Invoice '$invoiceId' was already added by this account"))

    else
      Effect
        .persist(InvoiceAdded(invoiceId, amount, totalAmount+amount))
        .thenReply(replyTo)(updatedAccount => Accepted(invoiceId = invoiceId, amount))
  }

  private def onRemoveInvoice(invoiceId: String,
                              amount: Int,
                              replyTo: ActorRef[Confirmation]
                             ): ReplyEffect[Event, Accountant] = {
    if (invoices.contains(invoiceId))
      Effect
        .persist(InvoiceRemoved(invoiceId))
        .thenReply(replyTo)(updatedAccountant => Accepted(invoiceId, amount))
    else
      Effect.reply(replyTo)(Accepted(invoiceId, amount)) // removing an item is idempotent
  }

  private def onAdjustInvoiceAmount(
                                    invoiceId: String,
                                    amount: Int,
                                    replyTo: ActorRef[Confirmation]
                                  ): ReplyEffect[Event, Accountant] = {
    if (amount <= 0)
      Effect.reply(replyTo)(Rejected("Amount must be greater than zero"))
    else if (invoices.contains(invoiceId))
      Effect
        .persist(InvoiceAmountAdjusted(invoiceId, amount))
        .thenReply(replyTo)(updatedCart => Accepted(invoiceId, amount))
    else
      Effect.reply(replyTo)(Rejected(s"Cannot adjust amount for invoice '$invoiceId'. Invoice not present in list"))
  }

  private def onGet(replyTo: ActorRef[Summary]): ReplyEffect[Event, Accountant] = {
    reply(replyTo)(Summary(this.invoices))
  }

  private def onResultGenerated(replyTo: ActorRef[Confirmation]): ReplyEffect[Event, Accountant] = {
    if (invoices.isEmpty)
      Effect.reply(replyTo)(Rejected("Cannot generates result, an empty invoice list"))
    else
      Effect
        .persist(ResultGenerated(Instant.now()))
        .thenReply(replyTo)(updatedCart => Accepted("resultId", totalAmount))
  }


  def applyEvent(evt: Event): Accountant =
    evt match {
      case InvoiceAdded(invoiceId, withAmount, amountTillNow)     => onInvoiceAddedOrUpdated(invoiceId, withAmount, amountTillNow)
      case InvoiceRemoved(invoiceId)                              => onInvoiceRemoved(invoiceId)
      case ResultGenerated(checkedOutTime)                        => onResultGenerated(checkedOutTime)
    }

  private def onInvoiceRemoved(invoiceId: String): Accountant =
    copy(invoices = invoices - invoiceId, totalAmount = totalAmount - invoices.getOrElse(invoiceId, 0))

  private def onInvoiceAddedOrUpdated(invoiceId: String, amount: Int, tillAmount: Int): Accountant =
    copy(invoices = invoices + (invoiceId -> amount), totalAmount = tillAmount)

  private def onResultGenerated(checkedOutTime: Instant): Accountant = {
    copy(totalAmount = 1245)
  }

}

object AccountantSerializerRegistry extends JsonSerializerRegistry {
  import Accountant._

  override def serializers: immutable.Seq[JsonSerializer[_]] =
    Seq(
      JsonSerializer[Accountant],
      JsonSerializer[InvoiceAdded],
      JsonSerializer[InvoiceRemoved],
      JsonSerializer[InvoiceAmountAdjusted],
      JsonSerializer[ResultGenerated],
      JsonSerializer[Summary],
      JsonSerializer[Confirmation],
      JsonSerializer[Accepted],
      JsonSerializer[Rejected],
    )
}
