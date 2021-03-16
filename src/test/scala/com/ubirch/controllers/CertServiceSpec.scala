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

      val expectedBody = """{"pubKeyInfo":{"algorithm":"ecdsa-p256v1","created":"2021-02-07T19:24:23.104Z","hwDeviceId":"c0eee73e-0ee5-40ed-b021-d9717e26330e","pubKey":"bO8heVJf38kG42x4c/pRANT7SRNSz0XUQ/nT+FT0lRGvjos9DF9h54eC9RYpY3AYNUPrkTFgqhE0W5twkGGe7Q==","pubKeyId":"bO8heVJf38kG42x4c/pRANT7SRNSz0XUQ/nT+FT0lRGvjos9DF9h54eC9RYpY3AYNUPrkTFgqhE0W5twkGGe7Q==","validNotAfter":"2021-08-07T19:24:23.104Z","validNotBefore":"2021-02-07T19:24:23.104Z"},"signature":"MEUCID8dhTVbusjVJV8vIS16knEebwOkUFndsZLdqQgMlBZiAiEAwIwYjh/5epNH6rhNaSjYsMpLOP3QbXi3+M4u3lCPAE4="}""".stripMargin

      post("/key/v1/pubkey", body = expectedBody) {
        status should equal(200)
        body should equal(expectedBody)
      }

      val bytes = loadFixture("src/main/resources/fixtures/3_CSR.der")
      post("/v1/csr/register", body = bytes) {
        println(body)
        assert(jsonConverter.as[PublicKeyInfo](body).isRight)
        status should equal(200)
      }
    }

    "register CSR with Ed25519" taggedAs Tag("durian") in {

      val expectedBody = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2021-02-07T18:53:09.168Z","hwDeviceId":"0eee73e-0ee5-40ed-b021-d9717e26330e","pubKey":"RyBMw0jYLO/kWSUFlg1bUZPHCqytr4Fpucrio2/b/kU=","pubKeyId":"RyBMw0jYLO/kWSUFlg1bUZPHCqytr4Fpucrio2/b/kU=","validNotAfter":"2021-08-07T18:53:09.168Z","validNotBefore":"2021-02-07T18:53:09.168Z"},"signature":"9VBGAzadr8SDks0oACUqyIiPU5tfV2fKFT+IjqrRH29IDvcwMgyJzbqmCwzQF5sxUcPgCzwAjoS2Cok+EpTuDw=="}""".stripMargin

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
