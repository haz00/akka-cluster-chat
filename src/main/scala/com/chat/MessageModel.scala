package com.chat

import java.time.LocalDateTime

class MessageModel(val username: String,
                   val text: String,
                   val time: LocalDateTime,
                   val isYou: Boolean) {

}
