package com.ubirch.services.kafka

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.services.lifeCycle.Lifecycle
import javax.inject._
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }

import scala.concurrent.ExecutionContext

abstract class Tiger(val config: Config, lifecycle: Lifecycle)
  extends ExpressKafka[String, String, Unit] with LazyLogging {

  override val keyDeserializer: Deserializer[String] = new StringDeserializer
  override val valueDeserializer: Deserializer[String] = new StringDeserializer
  override val consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPICS_PATH).split(",").toSet.filter(_.nonEmpty)
  override val keySerializer: Serializer[String] = new StringSerializer
  override val valueSerializer: Serializer[String] = new StringSerializer
  override val consumerBootstrapServers: String = config.getString(ConsumerConfPaths.BOOTSTRAP_SERVERS)
  override val consumerGroupId: String = config.getString(ConsumerConfPaths.GROUP_ID_PATH)
  override val consumerMaxPollRecords: Int = config.getInt(ConsumerConfPaths.MAX_POLL_RECORDS)
  override val consumerGracefulTimeout: Int = config.getInt(ConsumerConfPaths.GRACEFUL_TIMEOUT_PATH)
  override val metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)
  override val consumerReconnectBackoffMsConfig: Long = config.getLong(ConsumerConfPaths.RECONNECT_BACKOFF_MS_CONFIG)
  override val consumerReconnectBackoffMaxMsConfig: Long = config.getLong(ConsumerConfPaths.RECONNECT_BACKOFF_MAX_MS_CONFIG)
  override val maxTimeAggregationSeconds: Long = 120
  override val producerBootstrapServers: String = config.getString(ProducerConfPaths.BOOTSTRAP_SERVERS)
  override val lingerMs: Int = config.getInt(ProducerConfPaths.LINGER_MS)

}

@Singleton
class DefaultTiger @Inject() (config: Config, lifecycle: Lifecycle)(implicit val ec: ExecutionContext) extends Tiger(config, lifecycle) {

  override def process: Process = Process { crs =>
    println(crs.map(_.value()))
  }

  override def prefix: String = "Ubirch"

}
