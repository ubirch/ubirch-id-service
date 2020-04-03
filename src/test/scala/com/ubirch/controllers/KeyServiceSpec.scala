package com.ubirch.controllers

import java.nio.file.{ Files, Paths }
import java.util.{ Base64, UUID }

import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.ubirch.crypto.GeneratorKeyFactory
import com.ubirch.models.{ PublicKey, PublicKeyDelete, PublicKeyInfo }
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.util.{ DateUtil, PublicKeyUtil }
import com.ubirch.{ Binder, EmbeddedCassandra, InjectorHelper }
import net.manub.embeddedkafka.EmbeddedKafka
import org.joda.time.DateTime
import org.scalatest.Tag
import org.scalatra.test.scalatest.ScalatraWordSpec

import scala.language.postfixOps
import scala.util.Try

class KeyServiceSpec extends ScalatraWordSpec with EmbeddedCassandra with EmbeddedKafka {

  def loadFixture(resource: String) = {
    Files.readAllBytes(Paths.get(resource))
  }

  def getPublicKey(
      curveName: String,
      created: DateTime,
      validNotAfter: Option[DateTime],
      validNotBefore: DateTime,
      hardwareDeviceId: UUID = UUID.randomUUID()
  ) = {

    val curve = PublicKeyUtil.associateCurve(curveName)
    val newPrivKey = GeneratorKeyFactory.getPrivKey(curve)
    val newPublicKey = Base64.getEncoder.encodeToString(newPrivKey.getRawPublicKey)

    val pubKeyInfo = PublicKeyInfo(
      algorithm = curveName,
      created = created.toDate,
      hwDeviceId = hardwareDeviceId.toString,
      pubKey = newPublicKey,
      pubKeyId = newPublicKey,
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

    "get public key object when data exists" taggedAs Tag("mango") in {

      val expectedBody = """{"pubKeyInfo":{"algorithm":"ECC_ED25519","created":"2020-03-13T17:13:42.790Z","hwDeviceId":"e686b4ba-26b4-4a6d-8b57-f904299d4a5e","pubKey":"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=","pubKeyId":"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=","validNotAfter":"2021-03-13T23:13:42.790Z","validNotBefore":"2020-03-13T17:13:42.790Z"},"signature":"6m+hOG6bKGhOqCdBXVhnpJst+FpPcFUdn+JTpG7x6h0Ps5IlMIsX/kgXQjPWxXxN6T+eUSosZ9mkAZnfr8K3DA=="}""".stripMargin

      post("/v1/pubkey", body = expectedBody) {
        status should equal(200)
        body should equal(expectedBody)
      }

      get("/v1/pubkey/Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=") {
        status should equal(200)
        body should equal(expectedBody)
      }

    }

    "error parsing json" taggedAs Tag("plum") in {

      val incomingBody = """{"pubKeyInfo":{"algorithm":"ECC_ED25519","created":"2020-03-13T17:13:42.790Z","hwDeviceid":"e686b4ba-26b4-4a6d-8b57-f904299d4a5e","pubKey":"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=","pubKeyId":"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=","validNotAfter":"2021-03-13T23:13:42.790Z","validNotBefore":"2020-03-13T17:13:42.790Z"},"signature":"6m+hOG6bKGhOqCdBXVhnpJst+FpPcFUdn+JTpG7x6h0Ps5IlMIsX/kgXQjPWxXxN6T+eUSosZ9mkAZnfr8K3DA=="}""".stripMargin

      val expectedBody = """{"version":"1.0","status":"NOK","errorType":"ParsingError","errorMessage":"Couldn't parse [{\"pubKeyInfo\":{\"algorithm\":\"ECC_ED25519\",\"created\":\"2020-03-13T17:13:42.790Z\",\"hwDeviceid\":\"e686b4ba-26b4-4a6d-8b57-f904299d4a5e\",\"pubKey\":\"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=\",\"pubKeyId\":\"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=\",\"validNotAfter\":\"2021-03-13T23:13:42.790Z\",\"validNotBefore\":\"2020-03-13T17:13:42.790Z\"},\"signature\":\"6m+hOG6bKGhOqCdBXVhnpJst+FpPcFUdn+JTpG7x6h0Ps5IlMIsX/kgXQjPWxXxN6T+eUSosZ9mkAZnfr8K3DA==\"}] due to exception=org.json4s.package.MappingException message=No usable value for pubKeyInfo\nNo usable value for hwDeviceId\nDid not find value which can be converted into java.lang.String"}""".stripMargin

      post("/v1/pubkey", body = incomingBody) {
        status should equal(400)
        body should equal(expectedBody)
      }

    }

    "error parsing mspack" taggedAs Tag("papaya") in {

      val incomingBody = """{"pubKeyInfo":{"algorithm":"ECC_ED25519","created":"2020-03-13T17:13:42.790Z","hwDeviceid":"e686b4ba-26b4-4a6d-8b57-f904299d4a5e","pubKey":"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=","pubKeyId":"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=","validNotAfter":"2021-03-13T23:13:42.790Z","validNotBefore":"2020-03-13T17:13:42.790Z"},"signature":"6m+hOG6bKGhOqCdBXVhnpJst+FpPcFUdn+JTpG7x6h0Ps5IlMIsX/kgXQjPWxXxN6T+eUSosZ9mkAZnfr8K3DA=="}""".stripMargin

      val expectedBody = """{"version":"1.0","status":"NOK","errorType":"ParsingError","errorMessage":"Couldn't parse [7b227075624b6579496e666f223a7b22616c676f726974686d223a224543435f45443235353139222c2263726561746564223a22323032302d30332d31335431373a31333a34322e3739305a222c2268774465766963656964223a2265363836623462612d323662342d346136642d386235372d663930343239396434613565222c227075624b6579223a2242783359374f745647697372627764786d304f736449324359784930502f3142486532544b646c37742b303d222c227075624b65794964223a2242783359374f745647697372627764786d304f736449324359784930502f3142486532544b646c37742b303d222c2276616c69644e6f744166746572223a22323032312d30332d31335432333a31333a34322e3739305a222c2276616c69644e6f744265666f7265223a22323032302d30332d31335431373a31333a34322e3739305a227d2c227369676e6174757265223a22366d2b684f4736624b47684f714364425856686e704a73742b467050634655646e2b4a5470473778366830507335496c4d4973582f6b6758516a50577858784e36542b6555536f735a396d6b415a6e6672384b3344413d3d227d] due to exception=com.ubirch.protocol.ProtocolException message=msgpack decoding failed"}""".stripMargin

      post("/v1/pubkey/mpack", body = incomingBody.getBytes()) {
        status should equal(400)
        body should equal(expectedBody)
      }

    }

    "get public key object when data exists by hardware id " taggedAs Tag("cherry") in {

      val expectedBody = """{"pubKeyInfo":{"algorithm":"ECC_ED25519","created":"2020-03-13T17:13:42.790Z","hwDeviceId":"e686b4ba-26b4-4a6d-8b57-f904299d4a5e","pubKey":"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=","pubKeyId":"Bx3Y7OtVGisrbwdxm0OsdI2CYxI0P/1BHe2TKdl7t+0=","validNotAfter":"2021-03-13T23:13:42.790Z","validNotBefore":"2020-03-13T17:13:42.790Z"},"signature":"6m+hOG6bKGhOqCdBXVhnpJst+FpPcFUdn+JTpG7x6h0Ps5IlMIsX/kgXQjPWxXxN6T+eUSosZ9mkAZnfr8K3DA=="}""".stripMargin

      post("/v1/pubkey", body = expectedBody) {
        status should equal(200)
        body should equal(expectedBody)
      }

      get("/v1/pubkey/current/hardwareId/e686b4ba-26b4-4a6d-8b57-f904299d4a5e") {
        println(body)
        status should equal(200)
        body should equal("[" + expectedBody + "]")
      }

    }

    "get correct answer when data doesn't exist" taggedAs Tag("banana") in {

      val expectedBody = """{"version":"1.0","status":"NOK","errorType":"PubkeyError","errorMessage":"Key not found"}""".stripMargin

      get("/v1/pubkey/e686b4ba-26b4-4a6d-8b57-f904299d4a5") {
        status should equal(404)
        body should equal(expectedBody)
      }

    }

    "create key using the json endpoint" taggedAs Tag("orange") in {

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

    "create key using the json endpoint when pubKeyId is missing" taggedAs Tag("apricots") in {

      val pkAsString = """{"pubKeyInfo": {"algorithm": "ECC_ED25519", "created": "2020-04-03T12:45:32.000Z", "hwDeviceId": "3efabf03-3191-51ce-a074-d7680686ad95", "pubKey": "9eiyvS3i/beL8evwXLfUKRnELg/rqnibSnX1N/rxoLg=", "pubKeyId": "9eiyvS3i/beL8evwXLfUKRnELg/rqnibSnX1N/rxoLg=", "validNotAfter": "2021-04-03T12:45:32.000Z", "validNotBefore": "2020-04-03T12:45:32.000Z"}, "signature": "1BDdQDax/QQ0fCwSCwpI/a2MXEu8oTpWP2DtzE4NN1fzk4FTnoWKkPFBb8sIZcQpi5h7YNGLo9cy4LX/zPcHCQ=="}"""

      val expected = """{"pubKeyInfo":{"algorithm":"ECC_ED25519","created":"2020-04-03T12:45:32.000Z","hwDeviceId":"3efabf03-3191-51ce-a074-d7680686ad95","pubKey":"9eiyvS3i/beL8evwXLfUKRnELg/rqnibSnX1N/rxoLg=","pubKeyId":"9eiyvS3i/beL8evwXLfUKRnELg/rqnibSnX1N/rxoLg=","validNotAfter":"2021-04-03T12:45:32.000Z","validNotBefore":"2020-04-03T12:45:32.000Z"},"signature":"1BDdQDax/QQ0fCwSCwpI/a2MXEu8oTpWP2DtzE4NN1fzk4FTnoWKkPFBb8sIZcQpi5h7YNGLo9cy4LX/zPcHCQ=="}""".stripMargin

      post("/v1/pubkey", body = pkAsString) {
        status should equal(200)
        body should equal(expected)
      }

    }

    "create key using the mpack endpoint" taggedAs Tag("apple") in {

      val bytes = loadFixture("src/main/resources/fixtures/7_MsgPackKeyService.mpack")
      post("/v1/pubkey/mpack", body = bytes) {
        status should equal(200)
      }

    }

    "create key using the mpack endpoint from trackle message" taggedAs Tag("PassionFruit") in {

      val bytes = loadFixture("src/main/resources/fixtures/6_MsgPackKeyService.mpack")
      post("/v1/pubkey/mpack", body = bytes) {
        status should equal(200)
      }

    }

    "create key using the mpack endpoint from trackle messages" taggedAs Tag("Pear") in {

      val bytes1 = loadFixture("src/main/resources/fixtures/1_MsgPackKeyService.mpack")
      val bytes2 = loadFixture("src/main/resources/fixtures/2_MsgPackKeyService.mpack")
      val bytes3 = loadFixture("src/main/resources/fixtures/3_MsgPackKeyService.mpack")
      val bytes4 = loadFixture("src/main/resources/fixtures/4_MsgPackKeyService.mpack")
      val bytes5 = loadFixture("src/main/resources/fixtures/5_MsgPackKeyService.mpack")

      post("/v1/pubkey/mpack", body = bytes1) {
        status should equal(200)
      }

      post("/v1/pubkey/mpack", body = bytes2) {
        status should equal(200)
      }

      post("/v1/pubkey/mpack", body = bytes3) {
        status should equal(200)
      }

      post("/v1/pubkey/mpack", body = bytes4) {
        status should equal(200)
      }

      post("/v1/pubkey/mpack", body = bytes5) {
        status should equal(200)
      }

    }

    "create key using the json endpoint when no validNotAfter is provided" taggedAs Tag("watermelon") in {

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

    "delete key" taggedAs Tag("pineapple") in {

      val created = DateUtil.nowUTC
      val validNotAfter = Some(created.plusMonths(6))
      val validNotBefore = created

      (for {
        res <- getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore)
        (pk, pkAsString, _, _, pkr) = res
        signature <- Try(pkr.sign(pkr.getRawPublicKey)).toEither
        signatureAsString <- Try(Base64.getEncoder.encodeToString(signature)).toEither
        pubDelete = PublicKeyDelete(pk.pubKeyInfo.pubKeyId, signatureAsString)
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

    "delete key when multiple" taggedAs Tag("coconut") in {

      val created = DateUtil.nowUTC
      val validNotAfter = Some(created.plusMonths(6))
      val validNotBefore = created
      val hardwareDeviceId: UUID = UUID.randomUUID()

      (for {
        res1 <- getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore, hardwareDeviceId)
        (pk1, pkAsString1, _, _, pkr1) = res1
        signature1 <- Try(pkr1.sign(pkr1.getRawPublicKey)).toEither
        signatureAsString1 <- Try(Base64.getEncoder.encodeToString(signature1)).toEither
        pubDelete1 = PublicKeyDelete(pk1.pubKeyInfo.pubKeyId, signatureAsString1)
        pubDeleteAsString1 <- jsonConverter.toString[PublicKeyDelete](pubDelete1)

        res2 <- getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore, hardwareDeviceId)
        (pk2, pkAsString2, _, _, pkr2) = res2
        signature2 <- Try(pkr2.sign(pkr2.getRawPublicKey)).toEither
        signatureAsString2 <- Try(Base64.getEncoder.encodeToString(signature2)).toEither
        pubDelete2 = PublicKeyDelete(pk2.pubKeyInfo.pubKeyId, signatureAsString2)
        pubDeleteAsString2 <- jsonConverter.toString[PublicKeyDelete](pubDelete2)

        res3 <- getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore, hardwareDeviceId)
        (pk3, pkAsString3, _, _, pkr3) = res3
        signature3 <- Try(pkr3.sign(pkr3.getRawPublicKey)).toEither
        signatureAsString3 <- Try(Base64.getEncoder.encodeToString(signature3)).toEither
        pubDelete3 = PublicKeyDelete(pk3.pubKeyInfo.pubKeyId, signatureAsString3)

      } yield {

        post("/v1/pubkey", body = pkAsString1) {
          status should equal(200)
          body should equal(pkAsString1)
        }

        post("/v1/pubkey", body = pkAsString2) {
          status should equal(200)
          body should equal(pkAsString2)
        }

        post("/v1/pubkey", body = pkAsString3) {
          status should equal(200)
          body should equal(pkAsString3)
        }

        patch("/v1/pubkey", body = pubDeleteAsString1) {
          status should equal(200)
          body should equal("""{"version":"1.0","status":"OK","message":"Key deleted"}""")
        }

        patch("/v1/pubkey", body = pubDeleteAsString2) {
          status should equal(200)
          body should equal("""{"version":"1.0","status":"OK","message":"Key deleted"}""")
        }

        get("/v1/pubkey/current/hardwareId/" + hardwareDeviceId) {
          status should equal(200)
          body should equal(s"[$pkAsString3]")
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
