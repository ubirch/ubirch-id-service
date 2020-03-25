package com.ubirch.controllers

import java.util.{ Base64, UUID }

import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.ubirch.crypto.GeneratorKeyFactory
import com.ubirch.models.{ PublicKey, PublicKeyDelete, PublicKeyInfo }
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.util.{ DateUtil, ProtocolHelpers, PublicKeyUtil }
import com.ubirch.{ Binder, EmbeddedCassandra, InjectorHelper }
import net.manub.embeddedkafka.EmbeddedKafka
import org.apache.commons.codec.binary.Hex
import org.joda.time.DateTime
import org.scalatest.Tag
import org.scalatra.test.scalatest.ScalatraWordSpec

import scala.util.Try

class KeyServiceSpec extends ScalatraWordSpec with EmbeddedCassandra with EmbeddedKafka {

  def getPublicKey(
      curveName: String,
      created: DateTime,
      validNotAfter: Option[DateTime],
      validNotBefore: DateTime
  ) = {

    val curve = PublicKeyUtil.associateCurve(curveName)
    val newPrivKey = GeneratorKeyFactory.getPrivKey(curve)
    val newPublicKey = Base64.getEncoder.encodeToString(newPrivKey.getRawPublicKey)
    val hardwareDeviceId = UUID.randomUUID()

    val pubKeyUUID = UUID.randomUUID()
    val pubKeyInfo = PublicKeyInfo(
      algorithm = curveName,
      created = created.toDate,
      hwDeviceId = hardwareDeviceId.toString,
      pubKey = newPublicKey,
      pubKeyId = pubKeyUUID.toString,
      validNotAfter = validNotAfter.map(_.toDate),
      validNotBefore = validNotBefore.toDate
    )

    for {
      publicKeyInfoAsString <- jsonConverter.toString[PublicKeyInfo](pubKeyInfo)
      signatureAsBytes <- Try(newPrivKey.sign(publicKeyInfoAsString.getBytes)).toEither
      signature <- Try(Base64.getEncoder.encodeToString(signatureAsBytes)).toEither
      publicKey = PublicKey(pubKeyInfo, signature)
      publicKeyAsString <- jsonConverter.toString[PublicKey](publicKey)
    } yield {
      (publicKey, publicKeyAsString, signatureAsBytes, signature, newPrivKey)
    }

  }

  lazy val Injector = new InjectorHelper(List(new Binder)) {}

  val jsonConverter = Injector.get[JsonConverterService]

  "Key Service" must {

    "get public key object when data exists" in {

      val expectedBody = """{"pubKeyInfo":{"algorithm":"ECC_ED25519","created":"2020-03-13T17:13:42.790Z","hwDeviceId":"e686b4ba-26b4-4a6d-8b57-f904299d4a5e","pubKey":"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=","pubKeyId":"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=","validNotAfter":"2021-03-13T23:13:42.790Z","validNotBefore":"2020-03-13T17:13:42.790Z"},"signature":"6m+hOG6bKGhOqCdBXVhnpJst+FpPcFUdn+JTpG7x6h0Ps5IlMIsX/kgXQjPWxXxN6T+eUSosZ9mkAZnfr8K3DA=="}""".stripMargin

      post("/v1/pubkey", body = expectedBody) {
        status should equal(200)
        body should equal(expectedBody)
      }

      get("/v1/pubkey/e686b4ba-26b4-4a6d-8b57-f904299d4a5e") {
        status should equal(200)
        body should equal("[" + expectedBody + "]")
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

      val created = DateUtil.nowUTC
      val validNotAfter = Some(created.plusMonths(6))
      val validNotBefore = created

      getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore) match {
        case Right((_, pkAsString, _, _, _)) =>
          post("/v1/pubkey", body = pkAsString) {
            status should equal(200)
            body should equal(pkAsString)
          }
        case Left(e) =>
          fail(e)

      }
      getPublicKey(PublicKeyUtil.EDDSA, created, validNotAfter, validNotBefore) match {
        case Right((pk, pkAsString, _, _, _)) =>
          post("/v1/pubkey", body = pkAsString) {
            status should equal(200)
            body should equal(pkAsString)
          }
        case Left(e) =>
          fail(e)

      }

    }

    "create key using the mpack endpoint" taggedAs (Tag("current")) in {

      for {
        res <- ProtocolHelpers.packRandom(PublicKeyUtil.ECDSA)
        (bytes, _) = res
      } yield {
        post("/v1/pubkey/mpack", body = bytes) {
          status should equal(200)
        }
      }

      for {
        res <- ProtocolHelpers.packRandom(PublicKeyUtil.EDDSA)
        (bytes, _) = res
      } yield {
        post("/v1/pubkey/mpack", body = bytes) {
          status should equal(200)
        }
      }

    }

    "create key using the json endpoint when no validNotAfter is provided" in {

      val created = DateUtil.nowUTC
      val validNotAfter = None
      val validNotBefore = created

      getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore) match {
        case Right((_, pkAsString, _, _, _)) =>
          post("/v1/pubkey", body = pkAsString) {
            status should equal(200)
            body should equal(pkAsString)
          }
        case Left(e) =>
          fail(e)

      }
      getPublicKey(PublicKeyUtil.EDDSA, created, validNotAfter, created) match {
        case Right((_, pkAsString, _, _, _)) =>
          post("/v1/pubkey", body = pkAsString) {
            status should equal(200)
            body should equal(pkAsString)
          }
        case Left(e) =>
          fail(e)

      }

    }

    "delete key" in {

      val created = DateUtil.nowUTC
      val validNotAfter = Some(created.plusMonths(6))
      val validNotBefore = created

      (for {
        res <- getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore)
        (pk, pkAsString, _, _, pkr) = res
        signature <- Try(pkr.sign(pkr.getRawPublicKey)).toEither
        signatureAsString <- Try(Base64.getEncoder.encodeToString(signature)).toEither
        pubDelete = PublicKeyDelete(pk.pubKeyInfo.pubKey, signatureAsString)
        pubDeleteAsString <- jsonConverter.toString[PublicKeyDelete](pubDelete)

      } yield {

        post("/v1/pubkey", body = pkAsString) {
          status should equal(200)
          body should equal(pkAsString)
        }

        patch("/v1/pubkey", body = pubDeleteAsString) {
          status should equal(200)
          body should equal("""{"version":"1.0","status":"OK","message":"Key deleted"}""")
        }
      }).getOrElse(fail())

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
      )
    )

    super.beforeAll()
  }
}
