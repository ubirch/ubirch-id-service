package com.ubirch.services.kafka

import java.util.UUID
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.ubirch.ConfPaths.{ AnchoringProducerConfPaths, TigerConsumerConfPaths, TigerProducerConfPaths }
import com.ubirch._
import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.{ IdentitiesDAO, Identity, IdentityActivation, PublicKeyRowDAO }
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.util.cassandra.test.EmbeddedCassandraBase
import com.ubirch.util.{ CertUtil, Hasher, PublicKeyUtil }
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig }
import org.scalatest.Tag

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Test for the Tiger Engine
  */
class TigerSpec extends TestBase with EmbeddedCassandraBase with EmbeddedKafka {

  val cassandra = new CassandraTest

  def FakeInjector(bootstrapServers: String, importTopic: String, activationTopic: String) = new InjectorHelper(List(new Binder {
    override def Config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(new ConfigProvider {
      override def conf: Config = super.conf
        .withValue(TigerConsumerConfPaths.BOOTSTRAP_SERVERS, ConfigValueFactory.fromAnyRef(bootstrapServers))
        .withValue(TigerProducerConfPaths.BOOTSTRAP_SERVERS, ConfigValueFactory.fromAnyRef(bootstrapServers))
        .withValue(AnchoringProducerConfPaths.BOOTSTRAP_SERVERS, ConfigValueFactory.fromAnyRef(bootstrapServers))
        .withValue(TigerConsumerConfPaths.IMPORT_TOPIC_PATH, ConfigValueFactory.fromAnyRef(importTopic))
        .withValue(TigerConsumerConfPaths.ACTIVATION_TOPIC_PATH, ConfigValueFactory.fromAnyRef(activationTopic))

    })
  })) {}

  "read and process identities with success and errors" in {

    implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

    val importTopic = "com.ubirch.identity"
    val activationTopic = "com.ubirch.identity.activation"

    val Injector = FakeInjector("localhost:" + kafkaConfig.kafkaPort, importTopic, activationTopic)

    val jsonConverter = Injector.get[JsonConverterService]
    val identitiesDAO = Injector.get[IdentitiesDAO]

    val provider = PublicKeyUtil.keyPairGenerator

    val batch = 50
    def ownerId = UUID.randomUUID()
    val validIdentities = (1 to batch).map { _ =>
      val (_, _, id) = CertUtil.createCert(ownerId)(provider)
      val idAsString = jsonConverter.toString[Identity](id).getOrElse(throw new Exception("Not able to parse to string"))
      (id, idAsString)
    }

    val invalidIdentities = (1 to batch).map { _ =>
      val id = Identity(ownerId.toString, "", "sim_import", "", "this is a description")
      val idAsString = jsonConverter.toString[Identity](id).getOrElse(throw new Exception("Not able to parse to string"))
      (id, idAsString)
    }

    val totalIdentities = validIdentities ++ invalidIdentities

    withRunningKafka {

      totalIdentities.foreach { case (_, id) =>
        publishStringMessageToKafka(importTopic, id)
      }

      val tiger = Injector.get[Tiger]
      tiger.consumption.startPolling()

      Thread.sleep(7000)

      val presentIds = await(identitiesDAO.selectAll, 5 seconds)

      assert(presentIds.nonEmpty)
      assert(presentIds.size == validIdentities.size)

    }

  }

  "read and process identity activations with success and errors" taggedAs Tag("mango") in {

    implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

    val importTopic = "com.ubirch.identity"
    val activationTopic = "com.ubirch.identity.activation"

    val Injector = FakeInjector("localhost:" + kafkaConfig.kafkaPort, importTopic, activationTopic)

    val jsonConverter = Injector.get[JsonConverterService]
    val identitiesDAO = Injector.get[IdentitiesDAO]
    val publicKeyRowDAO = Injector.get[PublicKeyRowDAO]

    val provider = PublicKeyUtil.keyPairGenerator

    val batch = 50
    def ownerId = UUID.randomUUID()
    val validIdentities = (1 to batch).map { _ =>
      val (_, _, id) = CertUtil.createCert(ownerId)(provider)
      val idAsString = jsonConverter.toString[Identity](id).getOrElse(throw new Exception("Not able to parse to string"))
      (id, idAsString)
    }

    val invalidIdentities = (1 to batch).map { _ =>
      val id = Identity(ownerId.toString, "", "sim_import", "", "this is a description")
      val idAsString = jsonConverter.toString[Identity](id).getOrElse(throw new Exception("Not able to parse to string"))
      (id, idAsString)
    }

    val validIdentityActivations = validIdentities.map { case (id, _) =>
      val activation = IdentityActivation(id.ownerId, id.identityId, Hasher.hash(id.data))
      val activationAsString = jsonConverter.toString[IdentityActivation](activation).getOrElse(throw new Exception("Not able to parse to string"))
      (activation, activationAsString)
    }

    val totalIdentities = validIdentities ++ invalidIdentities

    withRunningKafka {

      totalIdentities.foreach { case (_, id) =>
        publishStringMessageToKafka(importTopic, id)
      }

      val tiger = Injector.get[Tiger]
      tiger.consumption.startPolling()

      Thread.sleep(7000)

      val presentIds = await(identitiesDAO.selectAll, 5 seconds)

      assert(presentIds.nonEmpty)
      assert(presentIds.size == validIdentities.size)

      validIdentityActivations.foreach { case (_, activation) =>
        publishStringMessageToKafka(activationTopic, activation)
      }

      Thread.sleep(9000)

      val presentKeys = await(publicKeyRowDAO.selectAll, 5 seconds)

      assert(validIdentities.size == presentKeys.size)

    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
    cassandra.executeScripts(List(EmbeddedCassandra.truncateScript))
  }

  protected override def afterAll(): Unit = {
    cassandra.stop()
  }

  protected override def beforeAll(): Unit = {
    cassandra.startAndExecuteScripts(EmbeddedCassandra.creationScripts, timeoutMS = 180000)
  }

}
