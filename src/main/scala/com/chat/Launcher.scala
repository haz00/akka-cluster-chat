package com.chat

import akka.NotUsed
import akka.actor.typed.ActorSystem
import com.chat.view.{MainController, Settings, SettingsView}
import com.typesafe.config.{Config, ConfigFactory}
import javafx.application.{Application, Platform}
import javafx.scene.Scene
import javafx.scene.control.{ButtonType, Dialog, DialogPane}
import javafx.stage.{Stage, WindowEvent}

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object Launcher {

  def main(args: Array[String]): Unit = {
    Application.launch(classOf[Launcher], args: _*)
  }
}

class Launcher extends Application {

  override def start(primaryStage: Stage): Unit = {

    val settings = promptSettings()

    val ctl = new MainController(settings.username.get())

    val config = getConfig(settings)
    val system = ActorSystem(ctl.defaultBehaviour(), "chat", config)

    primaryStage.setTitle("Chat - " + settings.username.get())
    primaryStage.setScene(new Scene(ctl, 500, 500))
    primaryStage.centerOnScreen()
    primaryStage.show()
    primaryStage.setOnCloseRequest(e => {
      ctl.setDisable(true) // disabled during system shutdown
      shutdownGracefully(e, system)
    })
  }

  private def getConfig(settings: Settings): Config = {
    val defaultConfig = ConfigFactory.load()
    val configOverload = ConfigFactory.parseString(
      s"""
    akka.remote.artery.canonical.host = ${settings.host}
    akka.remote.artery.canonical.port = ${settings.port}
    """)
    configOverload.withFallback(defaultConfig)
  }

  private def shutdownGracefully(e: WindowEvent, system: ActorSystem[NotUsed]): Unit = {
    // here we prevent the stage from closing for now
    // because this will block the JavaFX thread and the system will not be able to interact with our FxDispatcher (a deadlock has occurred)
    // so we have to wait for the system to finish on a dedicated thread and exit JavaFX when complete
    e.consume()

    implicit val ec = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())
    system.whenTerminated.onComplete { _ => Platform.exit() }

    system.terminate()
  }

  private def promptSettings(): Settings = {
    val model = new Settings()

    val dialogPane = new DialogPane()
    dialogPane.setGraphic(new SettingsView(model))
    dialogPane.getButtonTypes.add(ButtonType.OK)

    val dialog = new Dialog[ButtonType]()
    dialog.setTitle("Settings")
    dialog.setDialogPane(dialogPane)
    dialog.showAndWait().orElseThrow()

    model
  }
}