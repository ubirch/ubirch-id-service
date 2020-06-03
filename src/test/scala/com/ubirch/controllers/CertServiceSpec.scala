package com.ubirch.controllers

import com.github.nosan.embedded.cassandra.cql.CqlScript
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

  "CSR Service" must {

    "register CSR" taggedAs Tag("feijoa") in {
      val bytes = loadFixture("src/main/resources/fixtures/1_CSR.der")
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

    cassandra.executeScripts(
      CqlScript.statements("CREATE KEYSPACE identity_system WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"),
      CqlScript.statements("USE identity_system;"),
      CqlScript.statements("drop table if exists keys;"),
      CqlScript.statements(
        """
          |create table if not exists keys(
          |    pub_key          text,
          |    pub_key_id       text,
          |    hw_device_id     text,
          |    algorithm        text,
          |    valid_not_after  timestamp,
          |    valid_not_before timestamp,
          |    signature         text,
          |    raw               text,
          |    category          text,
          |    created           timestamp,
          |    PRIMARY KEY (pub_key_id, hw_device_id)
          |);
        """.stripMargin
      ),
      CqlScript.statements("drop MATERIALIZED VIEW IF exists keys_hw_device_id;"),
      CqlScript.statements(
        """
          |CREATE MATERIALIZED VIEW keys_hw_device_id AS
          |SELECT *
          |FROM keys
          |WHERE hw_device_id is not null
          |    and pub_key         is not null
          |    and pub_key_id       is not null
          |    and algorithm        is not null
          |    and valid_not_after  is not null
          |    and valid_not_before is not null
          |    and signature        is not null
          |    and raw              is not null
          |    and category         is not null
          |    and created          is not null
          |PRIMARY KEY (hw_device_id, pub_key_id);
          |""".stripMargin
      ),
      CqlScript.statements("drop MATERIALIZED VIEW IF exists keys_pub_key_id;"),
      CqlScript.statements(
        """
          |CREATE MATERIALIZED VIEW keys_pub_key_id AS
          |SELECT *
          |FROM keys
          |WHERE pub_key_id is not null
          |    and pub_key         is not null
          |    and hw_device_id     is not null
          |    and algorithm        is not null
          |    and valid_not_after  is not null
          |    and valid_not_before is not null
          |    and signature        is not null
          |    and raw              is not null
          |    and category         is not null
          |    and created          is not null
          |PRIMARY KEY (pub_key_id, hw_device_id);
          |""".stripMargin
      )
    )

    super.beforeAll()
  }
}
