package com.ubirch

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.ubirch.ConfPaths.{ AnchoringProducerConfPaths, TigerConsumerConfPaths, TigerProducerConfPaths }
import com.ubirch.services.config.ConfigProvider

/**
  * Customized Injector for changing values for tests
  * @param bootstrapServers Represents the bootstrap servers for kafka
  */
class InjectorHelperImpl(bootstrapServers: String) extends InjectorHelper(List(new Binder {
  override def Config: ScopedBindingBuilder = bind(classOf[Config])
    .toProvider(new ConfigProvider {
      override def conf: Config = {
        super.conf
          .withValue(TigerConsumerConfPaths.BOOTSTRAP_SERVERS, ConfigValueFactory.fromAnyRef(bootstrapServers))
          .withValue(TigerProducerConfPaths.BOOTSTRAP_SERVERS, ConfigValueFactory.fromAnyRef(bootstrapServers))
          .withValue(AnchoringProducerConfPaths.BOOTSTRAP_SERVERS, ConfigValueFactory.fromAnyRef(bootstrapServers))
      }
    })
}))
