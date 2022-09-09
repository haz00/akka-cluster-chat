package com.chat

import akka.actor.typed.ActorRef

object ChatCommand {

  trait Command

  case class InstancesChanged(actuals: Set[ActorRef[Command]]) extends Command

  case class InviteDialog(groupId: String,
                          inviter: ActorRef[Command],
                          username: String) extends Command

  case class GetInstanceInfo(replyTo: ActorRef[Command]) extends Command

  case class InstanceInfo(group: ActorRef[Command],
                          username: String) extends Command

  case class ReceiveMessage(sender: ActorRef[Command],
                            text: String) extends Command

  case object Dispose extends Command
}
