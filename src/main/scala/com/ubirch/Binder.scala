package com.ubirch

import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.services.cluster._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.formats.JsonFormatsProvider
import com.ubirch.services.kafka.{ DefaultTiger, Tiger }
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.rest.SwaggerProvider
import org.json4s.Formats
import org.scalatra.swagger.Swagger

import scala.concurrent.ExecutionContext

class Binder
  extends AbstractModule {

  def configure(): Unit = {

    bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
    bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
    bind(classOf[Config]).toProvider(classOf[ConfigProvider])
    bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
    bind(classOf[ClusterService]).to(classOf[DefaultClusterService])
    bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])
    bind(classOf[Tiger]).to(classOf[DefaultTiger])
    bind(classOf[Swagger]).toProvider(classOf[SwaggerProvider])
    bind(classOf[Formats]).toProvider(classOf[JsonFormatsProvider])

  }

}

object Binder {
  val modules: List[Module] = List(new Binder)
}
