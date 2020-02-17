package com.ubirch

import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.services.cluster._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.kafka.{ DefaultTiger, Tiger }
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }

import scala.concurrent.ExecutionContext

class IdServiceBinder
  extends AbstractModule {

  def configure(): Unit = {

    bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
    bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
    bind(classOf[Config]).toProvider(classOf[ConfigProvider])
    bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
    bind(classOf[ClusterService]).to(classOf[DefaultClusterService])
    bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])
    bind(classOf[Tiger]).to(classOf[DefaultTiger])

  }

}

object IdServiceBinder {
  val modules: List[Module] = List(new IdServiceBinder)
}
