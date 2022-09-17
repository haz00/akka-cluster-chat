package com.chat

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import akka.util.Timeout
import com.chat.GroupModel._
import javafx.collections.{FXCollections, ObservableList}

import java.time.LocalDateTime
import java.util
import scala.concurrent.duration.DurationInt
import scala.util.Success

class GroupModel(val name: String,
                 val id: String,
                 val ctl: MainController) {

  val messages: ObservableList[MessageModel] = FXCollections.observableList(new util.ArrayList[MessageModel]())
  val chatters: ObservableList[ChatterModel] = FXCollections.observableList(new util.ArrayList[ChatterModel]())

  private var self: ActorRef[Command] = _
  private implicit val timeout: Timeout = Timeout(3.second)

  def defaultBehaviour(): Behavior[Command] = Behaviors.setup { ctx =>
    self = ctx.self

    val groupKey = ServiceKey[Command](id)

    val receptionistAdapter: ActorRef[Receptionist.Listing] = ctx.messageAdapter[Receptionist.Listing] {
      case groupKey.Listing(actuals) => InstancesChanged(actuals)
    }

    ctx.system.receptionist ! Receptionist.Register(groupKey, ctx.self)
    ctx.system.receptionist ! Receptionist.Subscribe(groupKey, receptionistAdapter)

    EventSourcedBehavior[Command, Event, GroupState](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = GroupState(Vector.empty),
      commandHandler = (state, cmd) => {
        cmd match {
          case ReceiveMessage(sender, text) =>
            val msg = Message(sender, text, LocalDateTime.now(), sender == ctl.username)
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
            Effect.reply(replyTo)(InstanceInfo(ctx.self, ctl.username))

          case AddChatter(receiver, username) =>
            Effect.none.thenRun { _ =>
              if (chatters.stream().noneMatch(_.receiver == receiver)) {
                chatters.add(new ChatterModel(receiver, username, receiver == ctx.self))
                ctx.watchWith(receiver, RemoveChatter(receiver))
              }
            }

          case msg: JoinToDialog =>
            Effect.none.thenRun { _ => ctl.joinDialog(msg.groupId, msg.otherUsername) }

          case RemoveChatter(receiver) =>
            Effect.none.thenRun { _ => chatters.removeIf(_.receiver == receiver) }

          case Dispose => Effect.stop().thenStop()
        }
      },
      eventHandler = (state, event) => {
        event match {
          case e: AddMessage => state.addMessage(e.msg)
        }
      }
    ).receiveSignal {
      case (state, RecoveryCompleted) => state.messages.foreach(addMessage)
      case _ =>
    }
  }

  private def addMessage(msg: Message): Unit =
    messages.add(new MessageModel(msg.username, msg.text, msg.time, msg.self))

  def dispose(): Unit =
    self ! Dispose

  def sendMessage(text: String): Unit =
    chatters.forEach(_.receiver ! ReceiveMessage(ctl.username, text))

  def startDialog(other: ChatterModel): Unit =
    chatters.stream()
      .filter(_.receiver == self)
      .findFirst()
      .ifPresent(self => ctl.startDialog(self, other))
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

case class GroupState(messages: Vector[Message]) {

  def addMessage(msg: Message): GroupState =
    copy(messages = messages :+ msg)
}

case class Message(username: String, text: String, time: LocalDateTime, self: Boolean)