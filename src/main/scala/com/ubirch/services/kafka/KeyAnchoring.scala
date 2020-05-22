package com.ubirch.services.kafka

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.AnchoringProducerConfPaths
import com.ubirch.kafka.express.ExpressProducer
import com.ubirch.kafka.producer.{ ProducerRunner, WithProducerShutdownHook }
import com.ubirch.services.lifeCycle.Lifecycle
import javax.inject._
import org.apache.kafka.common.serialization.{ ByteArraySerializer, Serializer, StringSerializer }
import org.json4s.{ DefaultFormats, Formats }

import scala.concurrent.ExecutionContext

abstract class KeyAnchoring(config: Config, lifecycle: Lifecycle)(implicit ec: ExecutionContext)
  extends ExpressProducer[String, Array[Byte]]
  with WithProducerShutdownHook
  with LazyLogging {

  override val producerBootstrapServers: String = config.getString(AnchoringProducerConfPaths.BOOTSTRAP_SERVERS)
  override val lingerMs: Int = config.getInt(AnchoringProducerConfPaths.LINGER_MS)
  override val keySerializer: Serializer[String] = new StringSerializer
  override val valueSerializer: Serializer[Array[Byte]] = new ByteArraySerializer

  lifecycle.addStopHook(hookFunc(production))

}

@Singleton
class DefaultKeyAnchoring @Inject() (config: Config, lifecycle: Lifecycle)(implicit ec: ExecutionContext) extends KeyAnchoring(config, lifecycle) {

  implicit val formats: Formats = DefaultFormats

  override def production: ProducerRunner[String, Array[Byte]] = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))
}
