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

class Binder
  extends AbstractModule {

  def configure(): Unit = {

    bind(classOf[Config]).toProvider(classOf[ConfigProvider])
    bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
    bind(classOf[Scheduler]).toProvider(classOf[SchedulerProvider])
    bind(classOf[Swagger]).toProvider(classOf[SwaggerProvider])
    bind(classOf[Formats]).toProvider(classOf[JsonFormatsProvider])
    bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
    bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
    bind(classOf[PubKeyService]).to(classOf[DefaultPubKeyService])
    bind(classOf[ClusterService]).to(classOf[DefaultClusterService])
    bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])
    bind(classOf[Tiger]).to(classOf[DefaultTiger])

  }

}

object Binder {
  val modules: List[Module] = List(new Binder)
}
