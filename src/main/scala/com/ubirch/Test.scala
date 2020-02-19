package com.ubirch

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CountDownLatch

import com.ubirch.kafka.express.ExpressProducer
import com.ubirch.kafka.producer.ProducerRunner
import com.ubirch.models.Identity
import monix.execution.Scheduler
import org.apache.kafka.common.serialization.{ Serializer, StringSerializer }
import org.json4s.jackson.Serialization.write
import org.json4s.{ DefaultFormats, Formats }

import scala.util.{ Failure, Random, Success }

object Test {

  implicit lazy val scheduler: Scheduler = monix.execution.Scheduler(scala.concurrent.ExecutionContext.Implicits.global)

  def main(args: Array[String]): Unit = {

    implicit val formats: Formats = DefaultFormats
    val total = 1000000
    val count = new CountDownLatch(total)

    val producer = new ExpressProducer[String, Identity] {

      lazy val production: ProducerRunner[String, Identity] = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))

      override def producerBootstrapServers: String = "localhost:9092"

      override def lingerMs: Int = 600

      override def keySerializer: Serializer[String] = new StringSerializer

      override def valueSerializer: Serializer[Identity] = (_: String, data: Identity) => {
        write(data).getBytes(StandardCharsets.UTF_8)
      }
    }

    def createIdentity = {
      def identity = Identity(UUID.randomUUID().toString, "ABC", Random.nextString(300))
      producer.send("com.ubirch.identity", identity).onComplete {
        case Success(value) => count.countDown()
        case Failure(exception) => count.countDown()
      }
    }

    Iterator.continually(createIdentity).take(total).foreach(x => ())

    count.await()

  }
}
