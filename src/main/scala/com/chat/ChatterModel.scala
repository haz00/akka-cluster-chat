package com.chat

import akka.actor.typed.ActorRef

class ChatterModel(val receiver: ActorRef[GroupModel.Command],
                   val username: String,
                   var isYou: Boolean) {
}
