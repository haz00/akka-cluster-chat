package com.chat.view

import javafx.collections.ListChangeListener
import javafx.event.ActionEvent
import javafx.scene.control._
import javafx.scene.input.{KeyCode, KeyEvent}
import javafx.scene.layout.{BorderPane, Priority, VBox}

import java.time.format.DateTimeFormatter

class GroupView(val model: GroupModel) extends BorderPane {

  private val lvMessages = new ListView[MessageModel]()
  VBox.setVgrow(lvMessages, Priority.ALWAYS)
  lvMessages.setCellFactory((_: ListView[MessageModel]) =>new ChatListCell())

  private val txtInput = new TextField()
  txtInput.setOnKeyPressed(handleChatInput)

  private val lvChatters = new ListView[ChatterModel]()
  lvChatters.setCellFactory((_: ListView[ChatterModel]) => new ChatterListCell())

  private val miDialog = new MenuItem("Dialog")
  miDialog.setOnAction(handleDialogMenu)
  lvChatters.setContextMenu(new ContextMenu(miDialog))

  private val autoScrollListener = new ListChangeListener[MessageModel] {
    override def onChanged(c: ListChangeListener.Change[_ <: MessageModel]): Unit = {
      lvMessages.scrollTo(lvMessages.getItems.size())
    }
  }

  setCenter(new VBox(lvMessages, txtInput))
  setRight(lvChatters)

  bind(model)

  private def bind(model: GroupModel): Unit = {
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

  private class ChatterListCell extends ListCell[ChatterModel] {

    override def updateItem(item: ChatterModel, empty: Boolean): Unit = {
      super.updateItem(item, empty)
      textProperty().unbind()
      setText(null)
      setStyle("-fx-text-fill: -fx-text-inner-color") // default fx style

      if (item != null && !empty) {
        textProperty().bind(item.username)
        if (item.isYou) setStyle("-fx-text-fill: blue")
      }
    }
  }

  private class ChatListCell extends ListCell[MessageModel] {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    override def updateItem(item: MessageModel, empty: Boolean): Unit = {
      super.updateItem(item, empty)
      setText(null)
      setStyle("-fx-text-fill: -fx-text-inner-color") // default fx style

      if (item != null && !empty) {
        setText(formatMessage(item))
        if (item.sender.isYou) setStyle("-fx-text-fill: blue")
      }
    }

    private def formatMessage(msg: MessageModel): String =
      s"${msg.time.format(timeFormatter)} ${msg.sender.getUsername}: ${msg.text}"
  }
}
