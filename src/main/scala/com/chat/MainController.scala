package com.chat

import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import akka.cluster.typed.Cluster
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import com.chat.MainController._
import com.chat.view.{DialogView, GroupView}
import javafx.scene.control._
import javafx.scene.layout.BorderPane

import java.util.UUID
import scala.jdk.CollectionConverters.ListHasAsScala

class MainController(val username: String, val dispatcher: DispatcherSelector) extends BorderPane {

  private var self: ActorRef[Command] = _

  private val tabs = new TabPane()
  setCenter(tabs)

  def defaultBehaviour(): Behavior[NotUsed] = Behaviors.setup { ctx =>
    Cluster(ctx.system)
    ctx.spawn(running(), "fxBehaviour", dispatcher)
    Behaviors.empty
  }

  private def running(): Behavior[Command] = Behaviors.setup { ctx =>
    // fx-dispatcher thread

    self = ctx.self

    // create default group
    val group = new GroupModel("common", "common", this)
    ctx.spawnAnonymous(group.defaultBehaviour(), DispatcherSelector.sameAsParent())

    val mainTab = new Tab(group.name, new GroupView(group))
    mainTab.setClosable(false)
    tabs.getTabs.add(mainTab)

    EventSourcedBehavior[Command, Event, MainState](
      persistenceId = PersistenceId.ofUniqueId("main"),
      emptyState = MainState(List.empty),
      commandHandler = (state, cmd) => handleCommand(ctx, state, cmd),
      eventHandler = (state, event) => handleEvent(ctx, state, event)
    ).receiveSignal {
      case (state, RecoveryCompleted) => state.dialogs.foreach { d => addDialog(ctx, d) }
      case _ =>
    }
  }

  private def handleCommand(ctx: ActorContext[Command], state: MainState, cmd: Command): Effect[Event, MainState] =
    cmd match {
      case msg: AddDialog => Effect.persist(msg).thenRun { _ => addDialog(ctx, msg.dialog) }
      case msg: RemoveDialog => Effect.persist(msg)
      case _ => Effect.unhandled
    }

  private def handleEvent(ctx: ActorContext[Command], state: MainState, event: Event): MainState =
    event match {
      case e: AddDialog => state.addDialog(e.dialog)
      case e: RemoveDialog => state.removeDialog(e.id)
    }

  private def addDialog(ctx: ActorContext[Command], dialog: Dialogue): Unit = {
    val model = new GroupModel(s"[${dialog.otherUsername}]", dialog.id, this)
    val ref = ctx.spawnAnonymous(model.defaultBehaviour(), DispatcherSelector.sameAsParent())
    ctx.watchWith(ref, RemoveDialog(dialog.id))

    val view = new DialogView(model)

    val tab = new Tab(model.name, view)
    tab.setUserData(dialog.otherUsername)
    tab.setOnCloseRequest(_ => model.dispose())

    tabs.getTabs.add(tab)
    tabs.getSelectionModel.select(tab)
  }

  def startDialog(self: ChatterModel, other: ChatterModel): Unit = {
    if (other.isYou)
      return

    findDialogByUsername(other.username) match {
      case Some(exist) => tabs.getSelectionModel.select(exist)
      case None =>
        val groupId = UUID.randomUUID().toString

        self.receiver ! GroupModel.JoinToDialog(groupId, other.username)
        other.receiver ! GroupModel.JoinToDialog(groupId, self.username)
    }
  }

  def joinDialog(id: String, otherUsername: String): Unit =
    self ! AddDialog(Dialogue(id, otherUsername))

  private def findDialogByUsername(username: String): Option[Tab] =
    tabs.getTabs.asScala.collectFirst { case t if t.getUserData == username => t }
}

object MainController {

  private trait Command extends CborSerializable

  private trait Event extends CborSerializable

  private case class AddDialog(dialog: Dialogue) extends Command with Event

  private case class RemoveDialog(id: String) extends Command with Event
}

case class MainState(dialogs: List[Dialogue]) {

  def addDialog(dialog: Dialogue): MainState = copy(dialog :: dialogs)

  def removeDialog(id: String): MainState = copy(dialogs.filter(_.id != id))
}

case class Dialogue(id: String, otherUsername: String)