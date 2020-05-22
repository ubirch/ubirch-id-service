package com.ubirch.services.kafka

import java.util.UUID

import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.ubirch.ConfPaths.{ ConsumerConfPaths, TigerProducerConfPaths }
import com.ubirch._
import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.{ IdentitiesDAO, Identity }
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.formats.JsonConverterService
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig }

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Test for the Tiger Engine
  */
class TigerSpec extends TestBase with EmbeddedCassandra with EmbeddedKafka {

  def FakeInjector(bootstrapServers: String, consumerTopic: String) = new InjectorHelper(List(new Binder {
    override def Config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(new ConfigProvider {
      override def conf: Config = super.conf.withValue(
        ConsumerConfPaths.BOOTSTRAP_SERVERS,
        ConfigValueFactory.fromAnyRef(bootstrapServers)
      ).withValue(
          TigerProducerConfPaths.BOOTSTRAP_SERVERS,
          ConfigValueFactory.fromAnyRef(bootstrapServers)
        ).withValue(
            ConsumerConfPaths.TOPICS_PATH,
            ConfigValueFactory.fromAnyRef(consumerTopic)
          )

    })
  })) {}

  "read and process identities with success and errors" in {

    implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

    val incomingTopic = "com.ubirch.identity"

    val Injector = FakeInjector("localhost:" + kafkaConfig.kafkaPort, incomingTopic)

    val jsonConverter = Injector.get[JsonConverterService]
    val identitiesDAO = Injector.get[IdentitiesDAO]

    val batch = 50
    val validIdentities = (1 to batch).map { _ =>
      val id = Identity(UUID.randomUUID().toString, "sim_import", UUID.randomUUID().toString)
      val idAsString = jsonConverter.toString[Identity](id).getOrElse(throw new Exception("Not able to parse to string"))
      (id, idAsString)
    }

    val invalidIdentities = (1 to batch).map { _ =>
      val id = Identity(UUID.randomUUID().toString, "sim_import", "")
      val idAsString = jsonConverter.toString[Identity](id).getOrElse(throw new Exception("Not able to parse to string"))
      (id, idAsString)
    }

    val totalIdentities = validIdentities ++ invalidIdentities

    withRunningKafka {

      totalIdentities.foreach { case (_, id) =>
        publishStringMessageToKafka(incomingTopic, id)
      }

      val tiger = Injector.get[Tiger]
      tiger.consumption.startPolling()

      Thread.sleep(7000)

      val presentIds = await(identitiesDAO.selectAll, 5 seconds)

      assert(presentIds.nonEmpty)
      assert(presentIds.size == validIdentities.size)

    }

  }

  override protected def beforeEach(): Unit = {
    cassandra.executeScripts(
      CqlScript.statements("truncate identity_system.identities;")
    )
    CollectorRegistry.defaultRegistry.clear()
  }

  protected override def afterAll(): Unit = {
    cassandra.stop()
  }

  protected override def beforeAll(): Unit = {

    cassandra.start()

    cassandra.executeScripts(
      CqlScript.statements("CREATE KEYSPACE identity_system WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"),
      CqlScript.statements("USE identity_system;"),
      CqlScript.statements("drop table if exists identities;"),
      CqlScript.statements(
        """
          |create table if not exists identities (
          |    id text,
          |    category text,
          |    cert text,
          |    PRIMARY KEY (id, category)
          |);
        """.stripMargin
      )
    )

  }

}
