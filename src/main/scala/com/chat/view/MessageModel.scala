package com.chat.view

import java.time.LocalDateTime

class MessageModel(val sender: ChatterModel,
                   val text: String,
                   val time: LocalDateTime) {

}
