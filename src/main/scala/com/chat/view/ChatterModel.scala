package com.chat.view

import akka.actor.typed.ActorRef
import com.chat.ChatCommand

class ChatterModel(var username: Option[String],
                   val receiver: ActorRef[ChatCommand.Command],
                   val isYou: Boolean) {

  def getUsername: String = username.getOrElse("undefined")

  def hasUsername: Boolean = username.isDefined

  override def toString: String = getUsername
}
