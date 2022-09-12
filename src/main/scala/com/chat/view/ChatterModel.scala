package com.chat.view

import akka.actor.typed.ActorRef
import com.chat.ChatCommand
import javafx.beans.property.SimpleStringProperty

class ChatterModel(val receiver: ActorRef[ChatCommand.Command],
                   val isYou: Boolean) {

  val username = new SimpleStringProperty("undefined")

  def getUsername: String = username.get()
}
