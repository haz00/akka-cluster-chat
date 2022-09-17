package com.chat.view

import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleStringProperty
import javafx.scene.control.{Label, TextField}
import javafx.scene.layout.GridPane

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

final class Settings {
  val address = new SimpleStringProperty("127.0.0.1:4560")
  val username = new SimpleStringProperty("")
  val isUsernameBlank = Bindings.createBooleanBinding(() => username.get().isBlank(), username)

  def getHost: String = address.get().split(":")(0)

  def getPort: String = address.get().split(":")(1)

  def getUsername: String = username.get()
}
