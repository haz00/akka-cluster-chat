package com.chat.view

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
  var address = new SimpleStringProperty("127.0.0.1:4560")
  var username = new SimpleStringProperty()

  def host: String = address.get().split(":")(0)

  def port: String = address.get().split(":")(1)
}
