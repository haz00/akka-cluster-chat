package com.chat

import akka.dispatch.{DispatcherPrerequisites, ExecutorServiceConfigurator, ExecutorServiceFactory}
import com.typesafe.config.Config
import javafx.application.Platform

import java.util
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{AbstractExecutorService, ExecutorService, ThreadFactory, TimeUnit}

class FxExecutorService extends AbstractExecutorService {

  private val alive = new AtomicBoolean(true)

  def execute(command: Runnable): Unit = Platform.runLater(command)

  def shutdown(): Unit = alive.set(false)

  def shutdownNow(): util.List[Runnable] = {
    alive.set(false)
    Collections.emptyList[Runnable]
  }

  def isShutdown: Boolean = !alive.get()

  def isTerminated: Boolean = !alive.get()

  def awaitTermination(l: Long, timeUnit: TimeUnit): Boolean = !alive.get()
}

class FxServiceConfigurator(config: Config,
                            prerequisites: DispatcherPrerequisites)
  extends ExecutorServiceConfigurator(config, prerequisites) {

  override def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory =
    new ExecutorServiceFactory {
      override def createExecutorService: ExecutorService = new FxExecutorService()

    }
}
