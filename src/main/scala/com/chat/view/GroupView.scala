package com.chat.view

import javafx.collections.ListChangeListener
import javafx.event.ActionEvent
import javafx.scene.control.{ContextMenu, ListView, MenuItem, TextField}
import javafx.scene.input.{KeyCode, KeyEvent}
import javafx.scene.layout.{BorderPane, Priority, VBox}

class GroupView(val model: GroupModel,
                ctl: MainController) extends BorderPane {

  private val lvMessages = new ListView[String]()
  VBox.setVgrow(lvMessages, Priority.ALWAYS)

  private val txtInput = new TextField()
  txtInput.setOnKeyPressed(handleChatInput)

  private val lvChatters = new ListView[ChatterModel]()

  private val miDialog = new MenuItem("Dialog")
  miDialog.setOnAction(handleDialogMenu)
  lvChatters.setContextMenu(new ContextMenu(miDialog))

  private val autoScrollListener = new ListChangeListener[String] {
    override def onChanged(c: ListChangeListener.Change[_ <: String]): Unit = {
      lvMessages.scrollTo(lvMessages.getItems.size())
    }
  }

  setCenter(new VBox(lvMessages, txtInput))
  setRight(lvChatters)

  bind(model)

  def bind(model: GroupModel): Unit = {
    lvMessages.getItems.removeListener(autoScrollListener)

    lvMessages.setItems(model.messages)
    lvMessages.getItems.addListener(autoScrollListener)
    lvChatters.setItems(model.chatters)

    txtInput.requestFocus()
  }

  def handleChatInput(e: KeyEvent): Unit = {
    if (e.getCode == KeyCode.ENTER && model != null) {
      model.sendMessage(txtInput.getText)
      txtInput.clear()
    }
  }

  def handleDialogMenu(e: ActionEvent): Unit =
    model.startDialog(lvChatters.getSelectionModel.getSelectedItem)
}
