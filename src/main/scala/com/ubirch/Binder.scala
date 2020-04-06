package com.ubirch

import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.services.cluster._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.{ ExecutionProvider, SchedulerProvider }
import com.ubirch.services.formats.JsonFormatsProvider
import com.ubirch.services.kafka.{ DefaultTiger, Tiger }
import com.ubirch.services.key.{ DefaultPubKeyService, PubKeyService }
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.rest.SwaggerProvider
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.swagger.Swagger

import scala.concurrent.ExecutionContext

/**
  * Represents the default binder for the system components
  */
class Binder
  extends AbstractModule {

  def Config = bind(classOf[Config]).toProvider(classOf[ConfigProvider])
  def ExecutionContext = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
  def Scheduler = bind(classOf[Scheduler]).toProvider(classOf[SchedulerProvider])
  def Swagger = bind(classOf[Swagger]).toProvider(classOf[SwaggerProvider])
  def Formats = bind(classOf[Formats]).toProvider(classOf[JsonFormatsProvider])
  def Lifecycle = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
  def JVMHook = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
  def PubKeyService = bind(classOf[PubKeyService]).to(classOf[DefaultPubKeyService])
  def ClusterService = bind(classOf[ClusterService]).to(classOf[DefaultClusterService])
  def ConnectionService = bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])
  def Tiger = bind(classOf[Tiger]).to(classOf[DefaultTiger])

  def configure(): Unit = {
    Config
    ExecutionContext
    Scheduler
    Swagger
    Formats
    Lifecycle
    JVMHook
    PubKeyService
    ClusterService
    ConnectionService
    Tiger
  }

}

object Binder {
  def modules: List[Module] = List(new Binder)
}
