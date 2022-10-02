package com.chat.view

import javafx.beans.binding.{Bindings, BooleanBinding}
import javafx.beans.property.SimpleStringProperty
import javafx.scene.control.{ButtonType, Dialog, DialogPane, Label, TextField}
import javafx.scene.layout.GridPane

import scala.jdk.OptionConverters.RichOptional

class SettingsView(val model: Settings) extends GridPane {

  val txtAddress = new TextField()
  txtAddress.textProperty().bindBidirectional(model.address)

  val txtUsername = new TextField()
  txtUsername.textProperty().bindBidirectional(model.username)

  add(new Label("Address"), 0, 0)
  add(txtAddress, 1, 0)
  add(new Label("Username"), 0, 1)
  add(txtUsername, 1, 1)
}

object SettingsView {
  def promptSettings(defaultAddress: String = ""): Option[Settings] = {
    val model: Settings = new Settings()
    model.address.set(defaultAddress)

    val dialogPane = new DialogPane()
    dialogPane.setContent(new SettingsView(model))
    dialogPane.getButtonTypes.add(ButtonType.OK)
    dialogPane.lookupButton(ButtonType.OK).disableProperty().bind(model.isUsernameBlank)

    val dialog = new Dialog[Settings]()
    dialog.setResultConverter(btn => if (btn == ButtonType.OK) model else null)
    dialog.setTitle("Settings")
    dialog.setDialogPane(dialogPane)
    dialog.showAndWait().toScala
  }
}

final class Settings() {
  val address = new SimpleStringProperty("")
  val username = new SimpleStringProperty("")
  val isUsernameBlank: BooleanBinding = Bindings.createBooleanBinding(() => username.get().isBlank(), username)

  def getHost: String = address.get().split(":")(0)

  def getPort: String = address.get().split(":")(1)

  def getUsername: String = username.get()
}
