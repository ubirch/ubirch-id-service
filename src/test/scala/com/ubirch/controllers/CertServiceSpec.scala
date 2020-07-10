package com.ubirch.controllers

import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.{ NOK, PublicKeyInfo }
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.{ EmbeddedCassandra, InjectorHelperImpl, WithFixtures }
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig }
import org.scalatest.{ BeforeAndAfterEach, Tag }
import org.scalatra.test.scalatest.ScalatraWordSpec

/**
  * Test for the Cert Controller
  */
class CertServiceSpec extends ScalatraWordSpec with EmbeddedCassandra with EmbeddedKafka with WithFixtures with BeforeAndAfterEach {

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

      val expectedBody = """{"pubKeyInfo":{"algorithm":"ecdsa-p256v1","created":"2020-06-30T18:52:55.978Z","hwDeviceId":"c0eee73e-0ee5-40ed-b021-d9717e26330e","pubKey":"jlyU4AY0B8Xo7SmdL47Kix6xv9WjakRFexMrFHdxX8lD5Zo7+ZY7HiY9D6N/37bHVD8D5kTjfEkau38LR/ITrg==","pubKeyId":"jlyU4AY0B8Xo7SmdL47Kix6xv9WjakRFexMrFHdxX8lD5Zo7+ZY7HiY9D6N/37bHVD8D5kTjfEkau38LR/ITrg==","validNotAfter":"2020-12-30T18:52:55.978Z","validNotBefore":"2020-06-30T18:52:55.978Z"},"signature":"MEQCIE3pftyjycMTTz5NchzCER/8Kiw+SoNs8JeFiSmRwqKWAiAyljiyqZK2wA1Gg+gmcbMqa1CaHvg4097WnmFNso6ILg=="}""".stripMargin

      post("/key/v1/pubkey", body = expectedBody) {
        status should equal(200)
        body should equal(expectedBody)
      }

      val bytes = loadFixture("src/main/resources/fixtures/3_CSR.der")
      post("/v1/csr/register", body = bytes) {
        assert(jsonConverter.as[PublicKeyInfo](body).isRight)
        status should equal(200)
      }
    }

    "register CSR with Ed25519" taggedAs Tag("durian") in {

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
    EmbeddedCassandra.scripts.foreach(x => x.forEachStatement(connection.execute _))
  }

  protected override def afterAll(): Unit = {
    EmbeddedKafka.stop()
    cassandra.stop()
    super.afterAll()
  }

  protected override def beforeAll(): Unit = {

    EmbeddedKafka.start()
    cassandra.start()

    lazy val certController = Injector.get[CertController]
    lazy val keyController = Injector.get[KeyController]

    addServlet(certController, "/*")
    addServlet(keyController, "/key/*")

    super.beforeAll()
  }
}
