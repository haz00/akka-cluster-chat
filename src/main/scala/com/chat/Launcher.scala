package com.chat

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import com.chat.view.{Settings, SettingsView}
import com.typesafe.config.{Config, ConfigFactory}
import javafx.application.{Application, Platform}
import javafx.scene.Scene
import javafx.scene.control.{ButtonType, Dialog, DialogPane}
import javafx.stage.Stage

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object Launcher {

  def main(args: Array[String]): Unit = {
    Application.launch(classOf[Launcher], args: _*)
  }
}

class Launcher extends Application {

  private val shutdownService = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())

  override def start(primaryStage: Stage): Unit = {

    val settings = promptSettings()
    val config = getConfig(settings)

    val javafxDispatcher = DispatcherSelector.fromConfig("javafx-dispatcher")
    val ctl = new MainController(settings.getUsername, javafxDispatcher)

    val system = ActorSystem(ctl.defaultBehaviour(), "chat", config)
    system.whenTerminated.onComplete { _ => Platform.exit() }(shutdownService)

    primaryStage.setTitle(s"[${system.address.hostPort}] ${settings.getUsername}")
    primaryStage.setScene(new Scene(ctl, 500, 500))
    primaryStage.centerOnScreen()
    primaryStage.show()
    primaryStage.setOnCloseRequest(e => {
      e.consume()
      ctl.setDisable(true) // disabled during system shutdown
      system.terminate()
    })
  }

  override def stop(): Unit = shutdownService.shutdown()

  private def getConfig(settings: Settings): Config = {
    val defaultConfig = ConfigFactory.load()
    val configOverload = ConfigFactory.parseString(
      s"""
    akka.remote.artery.canonical.host = ${settings.getHost}
    akka.remote.artery.canonical.port = ${settings.getPort}
    akka.persistence.journal.leveldb.dir = journal/${settings.getUsername}
    """)
    configOverload.withFallback(defaultConfig)
  }

  private def promptSettings(): Settings = {
    val model = new Settings()

    val dialogPane = new DialogPane()
    dialogPane.setContent(new SettingsView(model))
    dialogPane.getButtonTypes.add(ButtonType.OK)
    dialogPane.lookupButton(ButtonType.OK).disableProperty().bind(model.isUsernameBlank)

    val dialog = new Dialog[ButtonType]()
    dialog.setTitle("Settings")
    dialog.setDialogPane(dialogPane)
    dialog.showAndWait().orElseThrow()

    model
  }
}