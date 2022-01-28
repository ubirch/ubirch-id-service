package com.ubirch.controllers

import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.{ NOK, PublicKeyInfo }
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.util.{ CertUtil, PublicKeyCreationHelpers, PublicKeyUtil }
import com.ubirch.{ EmbeddedCassandra, InjectorHelperImpl, WithFixtures }

import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig }
import org.scalatest.{ BeforeAndAfterEach, Tag }
import org.scalatra.test.scalatest.ScalatraWordSpec

import java.security.KeyPair
import java.util.UUID

/**
  * Test for the Cert Controller
  */
class CertServiceSpec extends ScalatraWordSpec with EmbeddedCassandra with EmbeddedKafka with WithFixtures with BeforeAndAfterEach {

  val cassandra = new CassandraTest

  implicit lazy val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

  lazy val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
  lazy val Injector = new InjectorHelperImpl(bootstrapServers) {}

  val jsonConverter = Injector.get[JsonConverterService]

  "CERT Service" must {
    "register x509 with ECDSA" taggedAs Tag("Gooseberries") in {
      val bytes = loadFixture("src/main/resources/fixtures/1_Cert_X509.der")

      var pubKeyRes: PublicKeyInfo = null

      post("/v1/cert/register", body = bytes) {
        jsonConverter.as[PublicKeyInfo](body) match {
          case Left(value) => fail(value)
          case Right(value) =>
            pubKeyRes = value
            assert(value.isInstanceOf[PublicKeyInfo])
        }
        status should equal(200)
      }

    }
  }

  "CSR Service" must {

    "fail if key is not previously known" taggedAs Tag("sugar-apple") in {
      val bytes = loadFixture("src/main/resources/fixtures/1_CSR.der")
      post("/v1/csr/register", body = bytes) {
        assert(jsonConverter.as[PublicKeyInfo](body).isLeft)
        status should equal(400)
      }
    }

    "register CSR with ECDSA when key exists" taggedAs Tag("feijoa") in {

      val uuid = UUID.randomUUID()
      val (keyAsString, keyPair) = PublicKeyCreationHelpers.randomPublicKey(
        hardwareDeviceId = uuid.toString,
        curve = "ecdsa-p256v1"
      ) match {
        case Left(e) => fail(e)
        case Right((_, keyAsString, _, _, privKey)) => (keyAsString, new KeyPair(privKey.getPublicKey, privKey.getPrivateKey))
      }

      val expectedBody = keyAsString

      post("/key/v1/pubkey", body = expectedBody) {
        status should equal(200)
        body should equal(expectedBody)
      }

      val csr = CertUtil.createCSR(uuid)(keyPair)
      val bytes = csr.getEncoded
      post("/v1/csr/register", body = bytes) {
        assert(jsonConverter.as[PublicKeyInfo](body).isRight)
        status should equal(200)
      }
    }

    //TODO: update fixtures
    "register CSR with Ed25519" taggedAs Tag("durian") ignore {

      val expectedBody = """{"pubKeyInfo":{"algorithm":"ED25519","created":"2020-07-01T06:13:01.333Z","hwDeviceId":"a0d1f73c-8819-4a97-b96b-49cabd3eba47","pubKey":"DxRWvEGJ5Dih861Kww/jYfGnLqV6oXwbE/aKLxFgiAk=","pubKeyId":"DxRWvEGJ5Dih861Kww/jYfGnLqV6oXwbE/aKLxFgiAk=","validNotAfter":"2021-01-01T06:13:01.333Z","validNotBefore":"2020-07-01T06:13:01.333Z"},"signature":"Ck2sUmbYk6rQmJEx6x2un0B9gfn5wIJWkgLtXDNc+rtD6+OKez5R2z2OPAzMhYLdXAKR06BgUIJBvie5nHQLCQ=="}""".stripMargin

      post("/key/v1/pubkey", body = expectedBody) {
        status should equal(200)
        body should equal(expectedBody)
      }

      val bytes = loadFixture("src/main/resources/fixtures/3_CSR_Ed25519.der")
      post("/v1/csr/register", body = bytes) {
        assert(jsonConverter.as[PublicKeyInfo](body).isRight)
        status should equal(200)
      }
    }

    "wrong uuid CSR" taggedAs Tag("carambola") in {
      val bytes = loadFixture("src/main/resources/fixtures/2_CSR_Wrong_UUID.der")
      post("/v1/csr/register", body = bytes) {
        assert(jsonConverter.as[NOK](body).isRight)
        status should equal(400)
      }
    }

    "wrong body" taggedAs Tag("cherimoya") in {
      post("/v1/csr/register", body = Array.empty) {
        assert(jsonConverter.as[NOK](body).isRight)
        status should equal(400)
      }
    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
    EmbeddedCassandra.truncateScript.forEachStatement(cassandra.connection.execute _)
  }

  protected override def afterAll(): Unit = {
    EmbeddedKafka.stop()
    cassandra.stop()
    super.afterAll()
  }

  protected override def beforeAll(): Unit = {

    EmbeddedKafka.start()
    cassandra.startAndCreateDefaults()

    lazy val certController = Injector.get[CertController]
    lazy val keyController = Injector.get[KeyController]

    addServlet(certController, "/*")
    addServlet(keyController, "/key/*")

    super.beforeAll()
  }
}
