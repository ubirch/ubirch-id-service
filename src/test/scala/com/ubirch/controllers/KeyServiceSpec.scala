package com.ubirch.controllers

import java.util.{ Base64, UUID }

import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.ubirch.crypto.GeneratorKeyFactory
import com.ubirch.models.{ PublicKey, PublicKeyInfo }
import com.ubirch.{ Binder, EmbeddedCassandra, InjectorHelper, TestBase }
import com.ubirch.services.formats.{ JsonConverterService, JsonFormatsProvider }
import com.ubirch.services.key.DefaultPubKeyService
import com.ubirch.util.{ DateUtil, PublicKeyUtil }
import net.manub.embeddedkafka.EmbeddedKafka
import org.joda.time.format.ISODateTimeFormat
import org.scalatest.{ MustMatchers, WordSpecLike }
import org.scalatra.test.scalatest.{ ScalatraSuite, ScalatraWordSpec }

import scala.util.Try

class KeyServiceSpec extends ScalatraWordSpec with EmbeddedCassandra with EmbeddedKafka {

  def getPublicKey(curveName: String) = {

    val curve = PublicKeyUtil.associateCurve(curveName)
    val newPrivKey = GeneratorKeyFactory.getPrivKey(curve)
    val newPublicKey = Base64.getEncoder.encodeToString(newPrivKey.getRawPublicKey)
    val hardwareDeviceId = UUID.randomUUID()

    val now = DateUtil.nowUTC
    val inSixMonths = now.plusMonths(6)
    val pubKeyUUID = UUID.randomUUID()
    val pubKeyInfo = PublicKeyInfo(
      algorithm = curveName,
      created = now.toDate,
      hwDeviceId = hardwareDeviceId.toString,
      pubKey = newPublicKey,
      pubKeyId = pubKeyUUID.toString,
      validNotAfter = Some(inSixMonths.toDate),
      validNotBefore = now.toDate
    )

    for {
      publicKeyInfoAsString <- jsonConverter.toString[PublicKeyInfo](pubKeyInfo)
      signature <- Try(Base64.getEncoder.encodeToString(newPrivKey.sign(publicKeyInfoAsString.getBytes))).toEither
      publicKey = PublicKey(pubKeyInfo, signature)
      publicKeyAsString <- jsonConverter.toString[PublicKey](publicKey)
    } yield {
      (publicKey, publicKeyAsString)
    }

  }

  lazy val Injector = new InjectorHelper(List(new Binder)) {}

  val jsonConverter = Injector.get[JsonConverterService]

  "Key Service" must {

    "get public key object when data exists" in {

      val expectedBody = """[{"pubKeyInfo":{"algorithm":"ECC_ED25519","created":"2020-03-13T16:13:42.790Z","hwDeviceId":"e686b4ba-26b4-4a6d-8b57-f904299d4a5e","pubKey":"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=","pubKeyId":"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=","validNotAfter":"2021-03-13T22:13:42.790Z","validNotBefore":"2020-03-13T16:13:42.790Z"},"signature":"6m+hOG6bKGhOqCdBXVhnpJst+FpPcFUdn+JTpG7x6h0Ps5IlMIsX/kgXQjPWxXxN6T+eUSosZ9mkAZnfr8K3DA=="}]""".stripMargin

      get("/v1/pubkey/e686b4ba-26b4-4a6d-8b57-f904299d4a5e") {
        status should equal(200)
        body should equal(expectedBody)
      }

    }
    "get correct answer when data doesn't exist" in {

      val expectedBody = """[]""".stripMargin

      get("/v1/pubkey/e686b4ba-26b4-4a6d-8b57-f904299d4a5") {
        status should equal(200)
        body should equal(expectedBody)
      }

    }

    "create key using the json endpoint" in {

      getPublicKey(PublicKeyUtil.ECDSA) match {
        case Right((_, pkAsString)) =>
          post("/v1/pubkey", body = pkAsString) {
            status should equal(200)
            body should equal(pkAsString)
          }
        case Left(e) =>
          fail(e)

      }
      getPublicKey(PublicKeyUtil.EDDSA) match {
        case Right((_, pkAsString)) =>
          post("/v1/pubkey", body = pkAsString) {
            status should equal(200)
            body should equal(pkAsString)
          }
        case Left(e) =>
          fail(e)

      }

    }
  }

  protected override def afterAll(): Unit = {
    EmbeddedKafka.stop()
    cassandra.stop()
    super.afterAll()
  }

  protected override def beforeAll(): Unit = {

    EmbeddedKafka.start()
    cassandra.start()

    lazy val keyController = Injector.get[KeyController]

    addServlet(keyController, "/*")

    cassandra.executeScripts(
      CqlScript.statements("CREATE KEYSPACE identity_system WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"),
      CqlScript.statements("USE identity_system;"),
      CqlScript.statements("drop table if exists keys;"),
      CqlScript.statements(
        """create table if not exists keys(
          |    pub_key          text,
          |    pub_key_id       text,
          |    hw_device_id     text,
          |    algorithm        text,
          |    valid_not_after  timestamp,
          |    valid_not_before timestamp,
          |    signature         text,
          |    raw               text,
          |    created           timestamp,
          |    PRIMARY KEY (pub_key, hw_device_id)
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
          |    and  pub_key          is not null
          |    and pub_key_id       is not null
          |    and hw_device_id     is not null
          |    and algorithm        is not null
          |    and valid_not_after  is not null
          |    and valid_not_before is not null
          |    and signature         is not null
          |    and raw               is not null
          |    and created           is not null
          |PRIMARY KEY (hw_device_id, pub_key);
          |""".stripMargin
      ),
      CqlScript.statements("INSERT INTO identity_system.keys (pub_key, hw_device_id, algorithm, created, pub_key_id, raw, signature, valid_not_after, valid_not_before) VALUES ('Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=', 'e686b4ba-26b4-4a6d-8b57-f904299d4a5e', 'ECC_ED25519', '2020-03-13 17:13:42.790', 'Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=', null, '6m+hOG6bKGhOqCdBXVhnpJst+FpPcFUdn+JTpG7x6h0Ps5IlMIsX/kgXQjPWxXxN6T+eUSosZ9mkAZnfr8K3DA==', '2021-03-13 23:13:42.790', '2020-03-13 17:13:42.790');")
    )

    super.beforeAll()
  }
}
