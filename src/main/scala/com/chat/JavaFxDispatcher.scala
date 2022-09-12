package com.chat

import akka.dispatch.{DispatcherPrerequisites, ExecutorServiceConfigurator, ExecutorServiceFactory}
import com.typesafe.config.Config
import javafx.application.Platform

import java.util
import java.util.Collections
import java.util.concurrent.{AbstractExecutorService, ExecutorService, ThreadFactory, TimeUnit}

class FxExecutorService extends AbstractExecutorService {

  def execute(command: Runnable): Unit = Platform.runLater(command)

  def shutdown(): Unit = {}

  def shutdownNow(): util.List[Runnable] = Collections.emptyList[Runnable]

  def isShutdown: Boolean = true

  def isTerminated: Boolean = true

  def awaitTermination(l: Long, timeUnit: TimeUnit): Boolean = true
}

class FxServiceConfigurator(config: Config,
                            prerequisites: DispatcherPrerequisites)
  extends ExecutorServiceConfigurator(config, prerequisites) {

  private val factory = new ExecutorServiceFactory {
    override def createExecutorService: ExecutorService = new FxExecutorService()
  }

  override def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = factory
}
