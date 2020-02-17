package com.ubirch.services.kafka

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.services.lifeCycle.Lifecycle
import javax.inject._
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }

import scala.concurrent.ExecutionContext

abstract class Tiger(val config: Config, lifecycle: Lifecycle)
  extends ExpressKafka[String, String, Unit] with LazyLogging {

  override val keyDeserializer: Deserializer[String] = new StringDeserializer
  override val valueDeserializer: Deserializer[String] = new StringDeserializer
  override val consumerTopics: Set[String] = config.getString("tiger.kafkaConsumer.topics").split(",").toSet.filter(_.nonEmpty)
  override val keySerializer: Serializer[String] = new StringSerializer
  override val valueSerializer: Serializer[String] = new StringSerializer
  override val consumerBootstrapServers: String = config.getString("tiger.kafkaConsumer.bootstrapServers")
  override val consumerGroupId: String = config.getString("tiger.kafkaConsumer.groupId")
  override val consumerMaxPollRecords: Int = config.getInt("tiger.kafkaConsumer.maxPollRecords")
  override val consumerGracefulTimeout: Int = config.getInt("tiger.kafkaConsumer.gracefulTimeout")
  override val producerBootstrapServers: String = config.getString("tiger.kafkaProducer.bootstrapServers")
  override val metricsSubNamespace: String = config.getString("tiger.kafkaConsumer.metricsSubNamespace")
  override val consumerReconnectBackoffMsConfig: Long = config.getLong("tiger.kafkaConsumer.reconnectBackoffMsConfig")
  override val consumerReconnectBackoffMaxMsConfig: Long = config.getLong("tiger.kafkaConsumer.reconnectBackoffMaxMsConfig")
  override val lingerMs: Int = config.getInt("tiger.kafkaProducer.lingerMS")
  override val maxTimeAggregationSeconds: Long = 120

}

@Singleton
class DefaultTiger @Inject() (config: Config, lifecycle: Lifecycle)(implicit val ec: ExecutionContext) extends Tiger(config, lifecycle) {

  override def process: Process = Process { crs =>
    println(crs.map(_.value()))
  }

  override def prefix: String = "Ubirch"

}
