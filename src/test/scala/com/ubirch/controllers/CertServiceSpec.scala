package com.ubirch.controllers

import com.ubirch.models.{ NOK, PublicKeyInfo }
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.{ Binder, EmbeddedCassandra, InjectorHelper, WithFixtures }
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.EmbeddedKafka
import org.scalatest.{ BeforeAndAfterEach, Tag }
import org.scalatra.test.scalatest.ScalatraWordSpec

import scala.language.postfixOps

/**
  * Test for the Cert Controller
  */
class CertServiceSpec extends ScalatraWordSpec with EmbeddedCassandra with EmbeddedKafka with WithFixtures with BeforeAndAfterEach {

  lazy val Injector = new InjectorHelper(List(new Binder)) {}

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

    "register CSR with ECDSA" taggedAs Tag("feijoa") in {
      val bytes = loadFixture("src/main/resources/fixtures/1_CSR.der")
      post("/v1/csr/register", body = bytes) {
        assert(jsonConverter.as[PublicKeyInfo](body).isRight)
        status should equal(200)
      }
    }

    "register CSR with Ed25519" taggedAs Tag("durian") in {
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

    addServlet(certController, "/*")

    cassandra.executeScripts(EmbeddedCassandra.scripts: _*)

    super.beforeAll()
  }
}
