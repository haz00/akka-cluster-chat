package com.chat.view

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import javafx.collections.{FXCollections, ObservableList}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util
import scala.collection.mutable

class GroupModel(val name: String,
                 val id: String,
                 val ctl: MainController) {

  import com.chat.ChatCommand._

  val messages: ObservableList[String] = FXCollections.observableList(new util.ArrayList[String]())
  val chatters: ObservableList[ChatterModel] = FXCollections.observableList(new util.ArrayList[ChatterModel]())

  private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

  private val instToModel = mutable.Map.empty[ActorRef[Command], ChatterModel]
  private var self: ActorRef[Command] = _

  def defaultBehaviour(): Behavior[Command] = Behaviors.setup { ctx =>

    self = ctx.self

    val groupKey = ServiceKey[Command](id)

    val receptionistAdapter: ActorRef[Receptionist.Listing] = ctx.messageAdapter[Receptionist.Listing] {
      case groupKey.Listing(actuals) => InstancesChanged(actuals)
    }

    ctx.system.receptionist ! Receptionist.Register(groupKey, ctx.self)
    ctx.system.receptionist ! Receptionist.Subscribe(groupKey, receptionistAdapter)

    Behaviors.receiveMessage {

      case ReceiveMessage(sender, text) =>
        instToModel.get(sender) match {
          case Some(model) if model.hasUsername => messages.add(formatMessage(model, text))
          case _ =>
        }
        Behaviors.same

      case InstancesChanged(actuals) =>
        // remove outdated instances
        instToModel.keys
          .filterNot(actuals.contains)
          .toSeq
          .map(instToModel.remove)
          .filter(_.isDefined)
          .map(_.get)
          .foreach(chatters.remove)

        // add the new ones
        actuals
          .filterNot(instToModel.contains)
          .foreach { actual =>
            instToModel.put(actual, new ChatterModel(None, actual, actual == self))
            actual ! GetInstanceInfo(ctx.self)
          }
        Behaviors.same

      case GetInstanceInfo(replyTo) =>
        replyTo ! InstanceInfo(ctx.self, ctl.username)
        Behaviors.same

      case InstanceInfo(inst, uname) =>
        instToModel.get(inst) match {
          case Some(model) if !chatters.contains(model) =>
            model.username = Some(uname)
            chatters.add(model)
          case _ =>
        }
        Behaviors.same

      case data: InviteDialog =>
        ctl.acceptDialog(data)
        Behaviors.same

      case Dispose =>
        Behaviors.stopped
    }
  }

  def dispose(): Unit =
    self ! Dispose

  def sendMessage(text: String): Unit =
    chatters.forEach { ch => ch.receiver ! ReceiveMessage(self, text) }

  def startDialog(other: ChatterModel): Unit =
    ctl.startDialog(self, other)

  override def toString: String = name

  private def formatMessage(sender: ChatterModel, text: String): String =
    new mutable.StringBuilder(if (sender.isYou) "[me] " else "")
      .append(timeFormatted).append(" ")
      .append(sender.getUsername).append(": ")
      .append(text)
      .toString()

  private def timeFormatted: String = LocalDateTime.now().toLocalTime.format(timeFormatter)
}
