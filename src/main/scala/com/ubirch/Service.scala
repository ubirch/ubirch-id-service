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

object Service extends Boot(List(new Binder)) {
  def main(args: Array[String]): Unit = * {
    get[IdentitySystem].start
  }
}

object DataInjectorTest {

  implicit lazy val scheduler: Scheduler = monix.execution.Scheduler(scala.concurrent.ExecutionContext.Implicits.global)
  implicit val formats: Formats = DefaultFormats

  def main(args: Array[String]): Unit = {

    val total = 1000000

    val producer = new ExpressProducer[String, Identity] {
      val keySerializer: Serializer[String] = new StringSerializer
      val valueSerializer: Serializer[Identity] = (_: String, data: Identity) => {
        write(data).getBytes(StandardCharsets.UTF_8)
      }
      val production: ProducerRunner[String, Identity] = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))
      val producerBootstrapServers: String = "localhost:9092"
      val lingerMs: Int = 600
    }

    val count = new CountDownLatch(total)

    def createIdentity = {
      def identity = Identity(UUID.randomUUID().toString, "ABC", Random.nextString(300))
      producer.send("com.ubirch.identity", identity).onComplete {
        case Success(value) => count.countDown()
        case Failure(exception) => count.countDown()
      }
    }

    Iterator.continually(createIdentity).take(total).foreach(_ => ())

    count.await()

  }
}
