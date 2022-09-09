package com.chat.view

import javafx.event.ActionEvent

class DialogView(model: GroupModel,
                 ctl: MainController) extends GroupView(model, ctl) {

  override def handleDialogMenu(e: ActionEvent): Unit = {}
}
