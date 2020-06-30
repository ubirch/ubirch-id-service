package com.ubirch
package services.kafka

import java.nio.charset.StandardCharsets

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.AnchoringProducerConfPaths
import com.ubirch.kafka.express.ExpressProducer
import com.ubirch.kafka.producer.{ ProducerRunner, WithProducerShutdownHook }
import com.ubirch.models.{ PublicKey, PublicKeyToAnchor }
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.TaskHelpers
import javax.inject._
import monix.eval.Task
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.{ ByteArraySerializer, Serializer, StringSerializer }
import org.json4s.{ DefaultFormats, Formats }

import scala.concurrent.TimeoutException
import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.language.postfixOps
import scala.util.Try

trait KeyAnchoring {
  def anchorKey(value: PublicKey): Task[RecordMetadata]
  def anchorKeyAsOpt(value: PublicKey): Task[Option[RecordMetadata]]
  def anchorKey(value: PublicKey, timeout: FiniteDuration = 10 seconds): Task[(RecordMetadata, PublicKey)]
  def publicKeyToAnchor(value: PublicKey): Try[PublicKeyToAnchor]
}

abstract class KeyAnchoringImpl(config: Config, lifecycle: Lifecycle, jsonConverterService: JsonConverterService)
  extends KeyAnchoring
  with ExpressProducer[String, Array[Byte]]
  with WithProducerShutdownHook
  with TaskHelpers
  with LazyLogging {

  override val producerBootstrapServers: String = config.getString(AnchoringProducerConfPaths.BOOTSTRAP_SERVERS)
  override val lingerMs: Int = config.getInt(AnchoringProducerConfPaths.LINGER_MS)
  override val keySerializer: Serializer[String] = new StringSerializer
  override val valueSerializer: Serializer[Array[Byte]] = new ByteArraySerializer

  val producerTopic: String = config.getString(AnchoringProducerConfPaths.TOPIC_PATH)

  override def publicKeyToAnchor(value: PublicKey): Try[PublicKeyToAnchor] = {
    Try(PublicKeyToAnchor(value.pubKeyInfo.hwDeviceId, value.pubKeyInfo.pubKey))
  }

  override def anchorKey(value: PublicKey): Task[RecordMetadata] = Task.defer {

    for {
      toPublish <- Task.fromTry(publicKeyToAnchor(value))
      toPublishAsJValue <- Task.fromTry(jsonConverterService.toJValue(toPublish).toTry.map(_.snakizeKeys))
      toPublishAsString <- Task.delay(jsonConverterService.toString(toPublishAsJValue))
      toPublishAsBytes <- Task.delay(toPublishAsString.getBytes(StandardCharsets.UTF_8))
      rm <- Task.fromFuture {
        send(
          producerTopic,
          toPublishAsBytes
        )
      }
    } yield {
      rm
    }

  }

  override def anchorKeyAsOpt(value: PublicKey): Task[Option[RecordMetadata]] = anchorKey(value)
    .map(x => Option(x))
    .onErrorHandle {
      e =>
        logger.error("Error publishing pubkey to kafka, pk={} exception={} error_message", value, e.getClass.getName, e.getMessage)
        None
    }

  override def anchorKey(value: PublicKey, timeout: FiniteDuration): Task[(RecordMetadata, PublicKey)] = {
    for {
      maybeRM <- anchorKeyAsOpt(value)
        .timeoutWith(timeout, FailedKafkaPublish(value, Option(new TimeoutException(s"failed_publish_timeout=${timeout.toString()}"))))
        .onErrorHandleWith(e => Task.raiseError(FailedKafkaPublish(value, Option(e))))
      _ = if (maybeRM.isEmpty) logger.error("failed_publish={}", value.toString)
      _ = if (maybeRM.isDefined) logger.info("publish_succeeded_for={}", value.pubKeyInfo.pubKeyId)
      _ <- earlyResponseIf(maybeRM.isEmpty)(FailedKafkaPublish(value, None))
    } yield {
      (maybeRM.get, value)
    }
  }

  lifecycle.addStopHook(hookFunc(production))

}

@Singleton
class DefaultKeyAnchoring @Inject() (config: Config, lifecycle: Lifecycle, jsonConverterService: JsonConverterService)
  extends KeyAnchoringImpl(config, lifecycle, jsonConverterService) {

  implicit val formats: Formats = DefaultFormats

  override def production: ProducerRunner[String, Array[Byte]] = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))
}
