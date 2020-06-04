package com.ubirch.services.kafka

import java.io.ByteArrayInputStream
import java.util.concurrent.ExecutionException

import com.datastax.driver.core.exceptions.{ InvalidQueryException, NoHostAvailableException }
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.kafka.util.Exceptions.NeedForPauseException
import com.ubirch.models.{ IdentitiesDAO, Identity }
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.Exceptions.StoringException
import javax.inject._
import monix.eval.Task
import monix.reactive.Observable
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization._
import org.json4s.jackson.Serialization._
import org.json4s.{ DefaultFormats, Formats }

import scala.concurrent.{ ExecutionContext, Promise }

/**
  * Represents the Express Kafka basic assembly for processing Identities.
  * @param config Represents the configuration object.
  * @param lifecycle Represents the life cycle object for the system.
  */
abstract class Tiger(val config: Config, lifecycle: Lifecycle)
  extends ExpressKafka[String, Array[Byte], Unit] with LazyLogging {

  override val keyDeserializer: Deserializer[String] = new StringDeserializer
  override val valueDeserializer: Deserializer[Array[Byte]] = new ByteArrayDeserializer
  override val consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPICS_PATH).split(",").toSet.filter(_.nonEmpty)
  override val keySerializer: Serializer[String] = new StringSerializer
  override val valueSerializer: Serializer[Array[Byte]] = new ByteArraySerializer
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

/**
  * Represents the default implementation of the Tiger abstraction. The processing logic is defined here.
  * @param identitiesDAO Represents the Data Access Object for the Identities.
  * @param config Represents the configuration object.
  * @param lifecycle Represents the life cycle object for the system.
  * @param ec Represents the execution context for async processes.
  */
@Singleton
class DefaultTiger @Inject() (identitiesDAO: IdentitiesDAO, config: Config, lifecycle: Lifecycle)(implicit val ec: ExecutionContext) extends Tiger(config, lifecycle) {

  implicit val formats: Formats = DefaultFormats

  def logic(consumerRecords: Vector[ConsumerRecord[String, Array[Byte]]]) = {
    val p = Promise[Unit]()
    Observable.fromIterable(consumerRecords)
      .map(_.value())
      .mapEval { bytes =>

        Task(read[Identity](new ByteArrayInputStream(bytes)))
          .map { identity =>
            if (identity.validate) identity
            else {
              throw new Exception("Identity received is not valid. The validation process failed: " + identity.toString)
            }
          }
          .doOnFinish { maybeError =>
            Task {
              maybeError.foreach { x =>
                logger.error("Error parsing: {}", x.getMessage)
              }
            }
          }
          .attempt

      }
      .collect {
        case Right(identity) => identity
      }
      .flatMap(identity => identitiesDAO.insert(identity))
      .onErrorHandle {
        case e: ExecutionException =>
          e.getCause match {
            case e: NoHostAvailableException =>
              logger.error("Error connecting to host: " + e)
              p.failure(NeedForPauseException("Error connecting", e.getLocalizedMessage))
            case e: InvalidQueryException =>
              logger.error("Error storing data (invalid query): " + e)
              p.failure(StoringException("Invalid Query ", e.getMessage))
          }
        case e: Exception =>
          logger.error("Error storing data (other): " + e)
          p.failure(StoringException("Error storing data (other)", e.getMessage))
      }
      .doOnComplete(Task(p.success(())))
      .foreachL(_ => ())
      .runToFuture(consumption.scheduler)

    p
  }

  override val process: Process = Process.async(logic(_).future)

  override def prefix: String = "Ubirch"

}
