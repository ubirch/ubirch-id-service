package com.ubirch.services.kafka

import java.nio.charset.StandardCharsets

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.AnchoringProducerConfPaths
import com.ubirch.kafka.express.ExpressProducer
import com.ubirch.kafka.producer.{ ProducerRunner, WithProducerShutdownHook }
import com.ubirch.models.PublicKeyRow
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.services.lifeCycle.Lifecycle
import javax.inject._
import monix.eval.Task
import monix.execution.Scheduler
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.{ ByteArraySerializer, Serializer, StringSerializer }
import org.json4s.{ DefaultFormats, Formats }

trait KeyAnchoring {
  def anchorKey(value: PublicKeyRow): Task[RecordMetadata]
}

abstract class KeyAnchoringImpl(config: Config, lifecycle: Lifecycle, jsonConverterService: JsonConverterService)(implicit scheduler: Scheduler)
  extends KeyAnchoring
  with ExpressProducer[String, Array[Byte]]
  with WithProducerShutdownHook
  with LazyLogging {

  override val producerBootstrapServers: String = config.getString(AnchoringProducerConfPaths.BOOTSTRAP_SERVERS)
  override val lingerMs: Int = config.getInt(AnchoringProducerConfPaths.LINGER_MS)
  override val keySerializer: Serializer[String] = new StringSerializer
  override val valueSerializer: Serializer[Array[Byte]] = new ByteArraySerializer

  val producerTopic: String = config.getString(AnchoringProducerConfPaths.TOPIC_PATH)

  override def anchorKey(value: PublicKeyRow): Task[RecordMetadata] = Task.defer {

    for {
      kd <- Task.fromTry(jsonConverterService.toString(value).toTry)
      rm <- Task.fromFuture {
        send(
          producerTopic,
          kd.getBytes(StandardCharsets.UTF_8)
        )
      }
    } yield {
      rm
    }

  }

  lifecycle.addStopHook(hookFunc(production))

}

@Singleton
class DefaultKeyAnchoring @Inject() (config: Config, lifecycle: Lifecycle, jsonConverterService: JsonConverterService)(implicit scheduler: Scheduler)
  extends KeyAnchoringImpl(config, lifecycle, jsonConverterService) {

  implicit val formats: Formats = DefaultFormats

  override def production: ProducerRunner[String, Array[Byte]] = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))
}
