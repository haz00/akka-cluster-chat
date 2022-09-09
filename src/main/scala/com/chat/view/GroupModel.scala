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

  private val instToModel = new util.HashMap[ActorRef[Command], ChatterModel]()
  private var self: ActorRef[Command] = _

  def defaultBehaviour(): Behavior[Command] = Behaviors.setup { ctx =>

    self = ctx.self

    val groupKey = ServiceKey[Command](id)
    var instances = Set.empty[ActorRef[Command]]

    val receptionistAdapter: ActorRef[Receptionist.Listing] = ctx.messageAdapter[Receptionist.Listing] {
      case groupKey.Listing(actuals) => InstancesChanged(actuals)
    }

    ctx.system.receptionist ! Receptionist.Register(groupKey, ctx.self)
    ctx.system.receptionist ! Receptionist.Subscribe(groupKey, receptionistAdapter)

    Behaviors.receiveMessage {

      case ReceiveMessage(sender, text) =>
        val model = instToModel.get(sender)
        if (model != null && model.hasUsername)
          messages.add(formatMessage(model, text))
        Behaviors.same

      case InstancesChanged(actuals) =>
        // remove outdated instances
        instances.foreach { inst =>
          if (!actuals.contains(inst)) {
            instances -= inst
            chatters.remove(instToModel.remove(inst))
          }
        }
        // add the new ones
        actuals.foreach { actual =>
          if (!instances.contains(actual)) {
            instances += actual

            if (!instToModel.containsKey(actual)) {
              instToModel.put(actual, new ChatterModel(None, actual, actual == self))
              actual ! GetInstanceInfo(ctx.self)
            }
          }
        }
        Behaviors.same

      case GetInstanceInfo(replyTo) =>
        replyTo ! InstanceInfo(ctx.self, ctl.username)
        Behaviors.same

      case InstanceInfo(inst, uname) =>
        val model = instToModel.get(inst)
        if (model != null && !chatters.contains(model)) {
          model.username = Some(uname)
          chatters.add(model)
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
