package com.ubirch

import java.util.{ Base64, UUID }

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.ubirch.ConfPaths.{ AnchoringProducerConfPaths, TigerConsumerConfPaths, TigerProducerConfPaths }
import com.ubirch.controllers.CertController
import com.ubirch.kafka.util.PortGiver
import com.ubirch.models._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.services.kafka.Tiger
import com.ubirch.services.key.PubKeyService
import com.ubirch.util.{ CertUtil, Hasher, PublicKeyUtil }
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig }
import org.bouncycastle.jcajce.BCFKSLoadStoreParameter.SignatureAlgorithm
import org.scalatest.BeforeAndAfterEach
import org.scalatra.test.scalatest.ScalatraWordSpec

import scala.concurrent.duration._
import scala.language.postfixOps

class ActivationRoundSpec extends ScalatraWordSpec with EmbeddedCassandra with EmbeddedKafka with WithFixtures with BeforeAndAfterEach with Awaits with ExecutionContextsTests {

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

  implicit lazy val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

  lazy val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

  val importTopic = "com.ubirch.identity"
  val activationTopic = "com.ubirch.identity.activation"

  val Injector = FakeInjector("localhost:" + kafkaConfig.kafkaPort, importTopic, activationTopic)

  "Identity Service" must {
    "complete total loop" in {

      val identitiesDAO = Injector.get[IdentitiesDAO]
      val identitiesByStateDAO = Injector.get[IdentityByStateDAO]
      val publicKeyRowDAO = Injector.get[PublicKeyRowDAO]
      val jsonConverter = Injector.get[JsonConverterService]
      val pubKeyService = Injector.get[PubKeyService]
      val certController = Injector.get[CertController]
      addServlet(certController, "/*")

      val keyPairGenerator = PublicKeyUtil.keyPairGenerator

      val algo = SignatureAlgorithm.SHA512withECDSA
      val ownerId = "03120206-0912-4020-9202-000017df4c73"
      val (kp, _, id) = CertUtil.createCert(UUID.fromString(ownerId))(keyPairGenerator, algo)
      val csr = CertUtil.createCSR(UUID.fromString(ownerId))(kp, algo)

      val curve = PublicKeyUtil.associateCurve(algo.name()).get
      val pubKey = pubKeyService.recreatePublicKey(kp.getPublic.getEncoded, curve).get

      withRunningKafka {

        //Publish identity := simulates import

        val identityId = Base64.getEncoder.encodeToString(pubKey.getRawPublicKey)

        val identity = id.copy(identityId = identityId)
        val identityAsString = jsonConverter.toString[Identity](identity).getOrElse(throw new Exception("Not able to parse to string"))

        publishStringMessageToKafka(importTopic, identityAsString)

        val tiger = Injector.get[Tiger]
        tiger.consumption.startPolling()

        Thread.sleep(5000)

        val presentIds = await(identitiesDAO.selectAll, 5 seconds).headOption

        assert(identity.ownerId == presentIds.map(_.ownerId).get)
        assert(identity.description == presentIds.map(_.description).get)
        assert(identity.data == presentIds.map(_.data).get)
        assert(identity.identityId == presentIds.map(_.identityId).get)

        //Activation: Creates key out of import data.

        val activation = IdentityActivation(identity.ownerId, identity.identityId, Hasher.hash(identity.data))
        val activationAsString = jsonConverter.toString[IdentityActivation](activation).getOrElse(throw new Exception("Not able to parse to string"))

        publishStringMessageToKafka(activationTopic, activationAsString)

        Thread.sleep(5000)

        val states = await(identitiesByStateDAO.selectAll, 5 seconds)

        assert(states.exists(_.state == X509Created.toString))
        assert(states.exists(_.state == X509KeyActivated.toString))

        assert(states.size == 2)

        val keys = await(publicKeyRowDAO.selectAll, 5 seconds)

        assert(keys.exists(_.pubKeyInfoRow.ownerId == ownerId))
        assert(keys.exists(_.pubKeyInfoRow.pubKey == identityId))
        assert(keys.exists(_.category == CERT.toString))
        assert(keys.exists(_.pubKeyInfoRow.algorithm == PublicKeyUtil.normalize(algo.name()).get))

        //Creates CSR

        post("/v1/csr/register", body = csr.getEncoded) {
          assert(jsonConverter.as[PublicKeyInfo](body).isRight)
          status should equal(200)
        }

        Thread.sleep(5000)

        val states2 = await(identitiesByStateDAO.selectAll, 5 seconds)

        assert(states2.exists(_.state == X509Created.toString))
        assert(states2.exists(_.state == X509KeyActivated.toString))
        assert(states2.exists(_.state == CSRCreated.toString))

        assert(states2.size == 3)

      }

    }
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
    cassandra.executeScripts(EmbeddedCassandra.scripts: _*)
  }

  protected override def afterAll(): Unit = {
    cassandra.stop()
    super.afterAll()
  }

  protected override def beforeAll(): Unit = {

    cassandra.start()

    super.beforeAll()
  }

}
