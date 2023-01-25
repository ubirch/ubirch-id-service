package com.ubirch.controllers

import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.{ NOK, PublicKeyInfo }
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.util.cassandra.test.EmbeddedCassandraBase
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
class CertServiceSpec extends ScalatraWordSpec with EmbeddedCassandraBase with EmbeddedKafka with WithFixtures with BeforeAndAfterEach {

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

    //How to generate: https://gist.github.com/Carlos-Augusto/b626a6c017ba4289aa0e71becd433c04
    "register CSR with Ed25519" taggedAs Tag("durian") in {

      val expectedBody = """{"pubKeyInfo":{"algorithm":"ED25519","created":"2022-02-21T15:54:50.980Z","hwDeviceId":"a273f05a-c6bc-4649-a1ef-88c9430d0420","pubKey":"x3iUyiICYaUqqS457ZP3GF3T116OaaMGIwND47SIPLI=","pubKeyId":"x3iUyiICYaUqqS457ZP3GF3T116OaaMGIwND47SIPLI=","validNotAfter":"2028-02-21T15:54:50.980Z","validNotBefore":"2022-02-21T15:54:50.980Z"},"signature":"asQmkurKQiuZfBDR3QWL+hQ1vZ8Ihr5Y5MMv6yzrQ5PT44chTODd8CsDz5o/9KX47pHGui+BMgxqVtW0Hfx2Ag=="}""".stripMargin

      post("/key/v1/pubkey", body = expectedBody) {
        status should equal(200)
        body should equal(expectedBody)
      }

      val bytes = loadFixture("src/main/resources/fixtures/a273f05a-c6bc-4649-a1ef-88c9430d0420.der")
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
    cassandra.executeScripts(List(EmbeddedCassandra.truncateScript))
  }

  protected override def afterAll(): Unit = {
    EmbeddedKafka.stop()
    cassandra.stop()
    super.afterAll()
  }

  protected override def beforeAll(): Unit = {

    EmbeddedKafka.start()
    cassandra.startAndExecuteScripts(EmbeddedCassandra.creationScripts)

    lazy val certController = Injector.get[CertController]
    lazy val keyController = Injector.get[KeyController]

    addServlet(certController, "/*")
    addServlet(keyController, "/key/*")

    super.beforeAll()
  }
}
