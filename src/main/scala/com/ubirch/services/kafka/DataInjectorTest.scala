package com.ubirch.services.kafka

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CountDownLatch

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.express.ExpressProducer
import com.ubirch.kafka.producer.ProducerRunner
import com.ubirch.models.Identity
import com.ubirch.util.{ CertUtil, PublicKeyUtil }
import monix.execution.Scheduler
import org.apache.kafka.common.serialization.{ Serializer, StringSerializer }
import org.json4s.jackson.Serialization.write
import org.json4s.{ DefaultFormats, Formats }

import scala.util.{ Failure, Success }

/**
  * Represents a simple tool to inject Identities for testing.
  */
object DataInjectorTest extends LazyLogging {

  implicit lazy val scheduler: Scheduler = monix.execution.Scheduler(scala.concurrent.ExecutionContext.Implicits.global)
  implicit val formats: Formats = DefaultFormats

  def main(args: Array[String]): Unit = {

    val total = 1000

    val producer = new ExpressProducer[String, Identity] {
      val keySerializer: Serializer[String] = new StringSerializer
      val valueSerializer: Serializer[Identity] = (_: String, data: Identity) => {
        write(data).getBytes(StandardCharsets.UTF_8)
      }
      val production: ProducerRunner[String, Identity] = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))
      override def producerBootstrapServers: String = "localhost:9092"
      val lingerMs: Int = 600
    }

    val count = new CountDownLatch(total)

    val provider = PublicKeyUtil.provider

    def createIdentity = {
      def identity = CertUtil.createCert(UUID.randomUUID())(provider)
      producer.send("com.ubirch.identity", identity).onComplete {
        case Success(_) => count.countDown()
        case Failure(exception) =>
          logger.error("error :=", exception)
          count.countDown()
      }
    }

    Iterator.continually(createIdentity).take(total).foreach(_ => ())

    count.await()

  }
}
