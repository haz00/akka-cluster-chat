package com.chat.view

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import akka.cluster.typed.Cluster
import com.chat.ChatCommand
import javafx.scene.control._
import javafx.scene.layout.BorderPane

import java.util.{Optional, UUID}

class MainController(val username: String) extends BorderPane {

  private var self: ActorRef[ChatCommand.InviteDialog] = _

  private val tabs = new TabPane()
  setCenter(tabs)

  def defaultBehaviour(): Behavior[NotUsed] = Behaviors.setup { ctx =>
    Cluster(ctx.system)
    ctx.spawn(running(), "fxBehaviour", DispatcherSelector.fromConfig("javafx-dispatcher"))
    Behaviors.empty
  }

  private def running(): Behavior[ChatCommand.InviteDialog] = Behaviors.setup { ctx =>
    // fx-dispatcher thread

    self = ctx.self

    val group = new GroupModel("common", "common", this)
    ctx.spawnAnonymous(group.defaultBehaviour(), DispatcherSelector.sameAsParent())

    val mainTab = new Tab(group.name, new GroupView(group, this))
    mainTab.setClosable(false)
    tabs.getTabs.add(mainTab)

    Behaviors.receiveMessage {
      invite: ChatCommand.InviteDialog =>
        val model = new GroupModel(s"[${invite.username}]", invite.groupId, this)
        ctx.spawnAnonymous(model.defaultBehaviour(), DispatcherSelector.sameAsParent())
        addDialog(model, invite.inviter)
        Behaviors.same
    }
  }

  private def addDialog(model: GroupModel, other: ActorRef[ChatCommand.Command]): DialogView = {
    val view = new DialogView(model, this)

    val tab = new Tab(model.name, view)
    tab.setUserData(other)
    tab.setOnCloseRequest(_ => model.dispose())

    tabs.getTabs.add(tab)
    tabs.getSelectionModel.select(tab)

    view
  }

  def startDialog(me: ActorRef[ChatCommand.Command], other: ChatterModel): Unit = {
    if (other.isYou)
      return

    val exist = findDialog(other.receiver)
    if (exist.isPresent)
      tabs.getSelectionModel.select(exist.get())
    else {
      val groupId = UUID.randomUUID().toString

      me ! ChatCommand.InviteDialog(groupId, other.receiver, username)
      other.receiver ! ChatCommand.InviteDialog(groupId, me, username)
    }
  }

  def acceptDialog(invite: ChatCommand.InviteDialog): Unit =
    self ! invite

  private def findDialog(other: ActorRef[ChatCommand.Command]): Optional[Tab] =
    tabs.getTabs.stream()
      .filter(t => t.getUserData == other)
      .findFirst()
}
