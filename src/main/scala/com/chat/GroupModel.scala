package com.chat

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.util.Timeout
import com.chat.GroupModel._
import javafx.collections.{FXCollections, ObservableList}

import java.time.LocalDateTime
import java.util
import scala.concurrent.duration.DurationInt
import scala.jdk.OptionConverters.RichOptional
import scala.util.Success

class GroupModel(val name: String,
                 val id: String,
                 val selfUsername: String) {

  val messages: ObservableList[MessageModel] = FXCollections.observableList(new util.ArrayList[MessageModel]())
  val chatters: ObservableList[ChatterModel] = FXCollections.observableList(new util.ArrayList[ChatterModel]())

  private var self: ActorRef[Command] = _
  private implicit val timeout: Timeout = Timeout(3.second)

  def defaultBehaviour(listener: InviteListener): Behavior[Command] = Behaviors.setup { ctx =>
    self = ctx.self

    val groupKey = ServiceKey[Command](id)

    val receptionistAdapter: ActorRef[Receptionist.Listing] = ctx.messageAdapter[Receptionist.Listing] {
      case groupKey.Listing(actuals) => InstancesChanged(actuals)
    }

    ctx.system.receptionist ! Receptionist.Register(groupKey, ctx.self)
    ctx.system.receptionist ! Receptionist.Subscribe(groupKey, receptionistAdapter)

    EventSourcedBehavior[Command, Event, GroupState](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = GroupState(List.empty),
      commandHandler = (state, cmd) => handleCommand(ctx, state, cmd, listener),
      eventHandler = (state, event) => handleEvent(ctx, state, event)
    ).receiveSignal {
      case (state, RecoveryCompleted) => state.messagesOrdered.foreach(addMessage)
      case _ =>
    }
  }

  private def handleCommand(ctx: ActorContext[Command],
                            state: GroupState,
                            cmd: Command,
                            listener: InviteListener): Effect[Event, GroupState] =
    cmd match {
      case ReceiveMessage(sender, text) =>
        val msg = Message(sender, text, LocalDateTime.now(), sender == selfUsername)
        Effect
          .persist(AddMessage(msg))
          .thenRun { _ => addMessage(msg) }

      case InstancesChanged(actuals) =>
        Effect.none.thenRun { _ =>
          actuals.foreach { actual =>
            ctx.ask(actual, GetInstanceInfo.apply) {
              case Success(info) => AddChatter(info.receiver, info.username)
            }
          }
        }

      case GetInstanceInfo(replyTo) =>
        Effect.reply(replyTo)(InstanceInfo(ctx.self, selfUsername))

      case AddChatter(receiver, username) =>
        Effect.none.thenRun { _ =>
          if (chatters.stream().noneMatch(_.receiver == receiver)) {
            chatters.add(new ChatterModel(receiver, username, receiver == ctx.self))
            ctx.watchWith(receiver, RemoveChatter(receiver))
          }
        }

      case msg: JoinToDialog =>
        Effect.none.thenRun { _ => listener.onInvite(msg.groupId, msg.otherUsername) }

      case RemoveChatter(receiver) =>
        Effect.none.thenRun { _ => chatters.removeIf(_.receiver == receiver) }

      case Dispose => Effect.stop().thenStop()
    }

  private def handleEvent(ctx: ActorContext[Command], state: GroupState, event: Event): GroupState =
    event match {
      case e: AddMessage => state.addMessage(e.msg)
    }

  private def addMessage(msg: Message): Unit =
    messages.add(new MessageModel(msg.username, msg.text, msg.time, msg.self))

  def dispose(): Unit =
    self ! Dispose

  def sendMessage(text: String): Unit =
    chatters.forEach(_.receiver ! ReceiveMessage(selfUsername, text))

  def getSelf: Option[ChatterModel] =
    chatters.stream()
      .filter(_.receiver == self)
      .findFirst()
      .toScala
}

trait InviteListener {
  def onInvite(groupId: String, otherUsername: String): Unit
}

object GroupModel {

  trait Command extends CborSerializable

  private trait Event extends CborSerializable

  case class JoinToDialog(groupId: String, otherUsername: String) extends Command

  case class GetInstanceInfo(replyTo: ActorRef[InstanceInfo]) extends Command

  case class InstanceInfo(receiver: ActorRef[Command], username: String) extends CborSerializable

  case class ReceiveMessage(sender: String, text: String) extends Command

  private case class AddChatter(receiver: ActorRef[Command], username: String) extends Command

  private case class RemoveChatter(receiver: ActorRef[Command]) extends Command

  private case class AddMessage(msg: Message) extends Event

  private case class InstancesChanged(actuals: Set[ActorRef[Command]]) extends Command

  private case object Dispose extends Command
}

case class GroupState(messages: List[Message]) {

  def addMessage(msg: Message): GroupState =
    copy(msg :: messages)

  def messagesOrdered: List[Message] = messages.reverse
}

case class Message(username: String, text: String, time: LocalDateTime, self: Boolean)