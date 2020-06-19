package com.ubirch.controllers

import java.util.{ Base64, UUID }

import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.{ NOK, PublicKey, PublicKeyDelete }
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.util.{ DateUtil, PublicKeyCreationHelpers, PublicKeyUtil }
import com.ubirch.{ EmbeddedCassandra, _ }
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig }
import org.scalatest.{ BeforeAndAfterEach, Tag }
import org.scalatra.test.scalatest.ScalatraWordSpec

import scala.language.postfixOps
import scala.util.Try

/**
  * Test for the Key Controller
  */
class KeyServiceSpec
  extends ScalatraWordSpec
  with EmbeddedCassandra
  with EmbeddedKafka
  with WithFixtures
  with BeforeAndAfterEach {

  implicit lazy val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

  lazy val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
  lazy val Injector = new InjectorHelperImpl(bootstrapServers) {}

  val jsonConverter = Injector.get[JsonConverterService]

  "Key Service" must {

    "get checks" taggedAs Tag("avocado") in {

      get("/v1/check") {
        status should equal(200)
        val expectedBody = """{"version":"1.0","status":"OK","message":"I survived a check"}"""
        body should equal(expectedBody)
      }

      get("/v1/deepCheck") {
        status should equal(200)
      }
    }

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
        status should equal(200)
        body should equal("[" + expectedBody + "]")
      }

    }

    "get public key object when data exists by hardware id when / is present" taggedAs Tag("lychee") in {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-06-09T09:50:26.083Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"Zk8/Lb9UKdFI07rTxAqrqmlQfEZH9w+2lAAXWUPIUYk=","pubKeyId":"Zk8/Lb9UKdFI07rTxAqrqmlQfEZH9w+2lAAXWUPIUYk=","validNotAfter":"2020-12-09T09:50:26.083Z","validNotBefore":"2020-06-09T09:50:26.083Z"},"signature":"Pqi2Tfs9sFsoWKzfAkUK6RYl+IkisHNpLcFju9nOS7IMQ/pJW0PFlUorz+NeA2EZThSCUaCAmQoywA/nMGABAA=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal("[" + dataKey1 + "]")
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-06-09T09:50:26.990Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"6MKCPw0iyAEBeW1qaK/qK+y86bzkiFb3SvC8uk3bIRk=","pubKeyId":"6MKCPw0iyAEBeW1qaK/qK+y86bzkiFb3SvC8uk3bIRk=","validNotAfter":"2020-12-09T09:50:26.990Z","validNotBefore":"2020-06-09T09:50:26.990Z"},"signature":"VcvUGzPBKsicQ2doqECSTrV+rmbJ992caBWwdJaKtu9sBtz2ukk8E7YlSELshYs1Slcw//GCVTXr8BjS5wl0DQ==","prevSignature":"JAwWDwkiNlpnNkzr7v3WSqgKXcG5j7XqFUU8N+8+F4Lk4T+vVd/4+uywoOzrpmlFnYlA+QbWciII81L2E/ZKDA=="}"""

      post("/v1/pubkey", body = dataKey2) {
        status should equal(200)
        body should equal(dataKey2)
      }

      val expectedKeys = """[{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-06-09T09:50:26.990Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"6MKCPw0iyAEBeW1qaK/qK+y86bzkiFb3SvC8uk3bIRk=","pubKeyId":"6MKCPw0iyAEBeW1qaK/qK+y86bzkiFb3SvC8uk3bIRk=","validNotAfter":"2020-12-09T09:50:26.990Z","validNotBefore":"2020-06-09T09:50:26.990Z"},"signature":"VcvUGzPBKsicQ2doqECSTrV+rmbJ992caBWwdJaKtu9sBtz2ukk8E7YlSELshYs1Slcw//GCVTXr8BjS5wl0DQ==","prevSignature":"JAwWDwkiNlpnNkzr7v3WSqgKXcG5j7XqFUU8N+8+F4Lk4T+vVd/4+uywoOzrpmlFnYlA+QbWciII81L2E/ZKDA=="},{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-06-09T09:50:26.083Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"Zk8/Lb9UKdFI07rTxAqrqmlQfEZH9w+2lAAXWUPIUYk=","pubKeyId":"Zk8/Lb9UKdFI07rTxAqrqmlQfEZH9w+2lAAXWUPIUYk=","validNotAfter":"2020-12-09T09:50:26.083Z","validNotBefore":"2020-06-09T09:50:26.083Z"},"signature":"Pqi2Tfs9sFsoWKzfAkUK6RYl+IkisHNpLcFju9nOS7IMQ/pJW0PFlUorz+NeA2EZThSCUaCAmQoywA/nMGABAA=="}]"""

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal(expectedKeys)
      }

    }

    "get public key object when data exists by public key id when / is present" taggedAs Tag("pummelo") in {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-05-22T13:56:10.656Z","hwDeviceId":"e41b1945-886c-4fc6-a044-9fcc509136fa","pubKey":"qQqY2Rf71GeT968YcEWzOZ2L8KKNIFY2eHxHqeINeCQ=","pubKeyId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","validNotAfter":"2020-11-22T13:56:10.656Z","validNotBefore":"2020-05-22T13:56:10.656Z"},"signature":"gufCxO6ISDP/K05DSNgLznI1FSdiK1QdSQhCSdyYR6Zq5S3w6iLr5dqCODZVd+aDKGkWYXzJMbZypLFZWUsoBg=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      get("/v1/pubkey/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal(dataKey1)
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-05-22T14:29:27.993Z","hwDeviceId":"d255d636-bcaa-47ef-9130-5b3b7e6ef748","pubKey":"yOnHwSOEAmkI24Gkp2g2gTwlvCx0awhzbsHyQnAqwr8=","pubKeyId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","validNotAfter":"2020-11-22T14:29:27.993Z","validNotBefore":"2020-05-22T14:29:27.993Z"},"signature":"N/En6GNjJuIkphiDofiVxSpmG2N27IRMp9veVLtiM1omxQH0A1l7VAfzjjM/BSXPBHq9DAQ4cqPad9prB6FmBg=="}"""

      post("/v1/pubkey", body = dataKey2) {
        status should equal(200)
        body should equal(dataKey2)
      }

      get("/v1/pubkey/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal(dataKey2)
      }

    }

    "get correct answer when data doesn't exist" taggedAs Tag("banana") in {

      val expectedBody = """{"version":"1.0","status":"NOK","errorType":"PubkeyError","errorMessage":"Key not found"}""".stripMargin

      get("/v1/pubkey/e686b4ba-26b4-4a6d-8b57-f904299d4a5") {
        status should equal(404)
        body should equal(expectedBody)
      }

    }

    "create key using the json endpoint when same key" taggedAs Tag("date_fruit") in {

      val created = DateUtil.nowUTC
      val validNotAfter = Some(created.plusMonths(6))
      val validNotBefore = created
      val hardwareDeviceId: String = UUID.randomUUID().toString

      PublicKeyCreationHelpers.getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore, hardwareDeviceId = hardwareDeviceId) match {

        case Right((_, pkAsString, _, _, _)) =>

          post("/v1/pubkey", body = pkAsString) {
            status should equal(200)
            body should equal(pkAsString)
          }

          post("/v1/pubkey", body = pkAsString) {
            status should equal(200)
            body should equal(pkAsString)
          }

        case Left(e) =>
          fail(e)

      }

      val anchors = consumeNumberStringMessagesFrom("com.ubirch.identity.key", 2)
      assert(anchors.nonEmpty)

    }

    "create key using the json endpoint when diff key" taggedAs Tag("figs") in {

      val created = DateUtil.nowUTC
      val validNotAfter = Some(created.plusMonths(6))
      val validNotBefore = created
      val hardwareDeviceId: String = UUID.randomUUID().toString

      PublicKeyCreationHelpers.getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore, hardwareDeviceId = hardwareDeviceId) match {
        case Right((_, pkAsString, _, _, _)) =>
          post("/v1/pubkey", body = pkAsString) {
            status should equal(200)
            body should equal(pkAsString)
          }
        case Left(e) =>
          fail(e)

      }

      PublicKeyCreationHelpers.getPublicKey(PublicKeyUtil.EDDSA, created, validNotAfter, validNotBefore, hardwareDeviceId = hardwareDeviceId) match {
        case Right((_, pkAsString, _, _, _)) =>
          post("/v1/pubkey", body = pkAsString) {
            status should equal(400)
            body should equal("""{"version":"1.0","status":"NOK","errorType":"PubkeyError","errorMessage":"Error creating pub key"}""")
          }
        case Left(e) =>
          fail(e)

      }

    }

    "create key using the json endpoint" taggedAs Tag("orange") in {

      val created = DateUtil.nowUTC
      val validNotAfter = Some(created.plusMonths(6))
      val validNotBefore = created

      PublicKeyCreationHelpers.getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore) match {
        case Right((_, pkAsString, _, _, _)) =>
          post("/v1/pubkey", body = pkAsString) {
            status should equal(200)
            body should equal(pkAsString)
          }
        case Left(e) =>
          fail(e)

      }
      PublicKeyCreationHelpers.getPublicKey(PublicKeyUtil.EDDSA, created, validNotAfter, validNotBefore) match {
        case Right((_, pkAsString, _, _, _)) =>
          post("/v1/pubkey", body = pkAsString) {
            status should equal(200)
            body should equal(pkAsString)
          }
        case Left(e) =>
          fail(e)

      }

      val anchors = consumeNumberStringMessagesFrom("com.ubirch.identity.key", 2)
      assert(anchors.nonEmpty)

    }

    "create key using the json endpoint when pubKeyId is missing" taggedAs Tag("apricots") in {

      val pkAsString = """{"pubKeyInfo": {"algorithm": "ECC_ED25519", "created": "2020-04-03T12:45:32.000Z", "hwDeviceId": "3efabf03-3191-51ce-a074-d7680686ad95", "pubKey": "9eiyvS3i/beL8evwXLfUKRnELg/rqnibSnX1N/rxoLg=", "pubKeyId": "9eiyvS3i/beL8evwXLfUKRnELg/rqnibSnX1N/rxoLg=", "validNotAfter": "2021-04-03T12:45:32.000Z", "validNotBefore": "2020-04-03T12:45:32.000Z"}, "signature": "1BDdQDax/QQ0fCwSCwpI/a2MXEu8oTpWP2DtzE4NN1fzk4FTnoWKkPFBb8sIZcQpi5h7YNGLo9cy4LX/zPcHCQ=="}"""

      val expected = """{"pubKeyInfo":{"algorithm":"ECC_ED25519","created":"2020-04-03T12:45:32.000Z","hwDeviceId":"3efabf03-3191-51ce-a074-d7680686ad95","pubKey":"9eiyvS3i/beL8evwXLfUKRnELg/rqnibSnX1N/rxoLg=","pubKeyId":"9eiyvS3i/beL8evwXLfUKRnELg/rqnibSnX1N/rxoLg=","validNotAfter":"2021-04-03T12:45:32.000Z","validNotBefore":"2020-04-03T12:45:32.000Z"},"signature":"1BDdQDax/QQ0fCwSCwpI/a2MXEu8oTpWP2DtzE4NN1fzk4FTnoWKkPFBb8sIZcQpi5h7YNGLo9cy4LX/zPcHCQ=="}""".stripMargin

      post("/v1/pubkey", body = pkAsString) {
        status should equal(200)
        body should equal(expected)
      }

      val anchors = consumeNumberStringMessagesFrom("com.ubirch.identity.key", 1)
      assert(anchors.nonEmpty)

    }

    "create key using the mpack endpoint" taggedAs Tag("apple") in {

      val expectedBody = """{"pubKeyInfo":{"algorithm":"ECC_ED25519","created":"2019-06-14T13:53:20.000Z","hwDeviceId":"55424952-3c71-bf88-1fa4-3c71bf881fa4","pubKey":"6LFYOnlZEbpIIfbRWVf7sqi2WJ+sDijwRp8dXUZOFzk=","pubKeyId":"6LFYOnlZEbpIIfbRWVf7sqi2WJ+sDijwRp8dXUZOFzk=","validNotAfter":"2020-06-04T13:53:20.000Z","validNotBefore":"2019-06-14T13:53:20.000Z"},"signature":"fde03123a4a784a825ea879216d4186b4729aead7c649d94aa0db72964fe8b3d2a4cdf5b1adf432b9df2f8af69215378fe30b3e9c5e2be4d27efa03d85538c0f"}"""

      val bytes = loadFixture("src/main/resources/fixtures/7_MsgPackKeyService.mpack")
      post("/v1/pubkey/mpack", body = bytes) {
        body should equal(expectedBody)
        status should equal(200)
      }

    }

    "create key twice using the mpack endpoint with same data" taggedAs Tag("jackfruit") in {

      val expectedBody = """{"pubKeyInfo":{"algorithm":"ECC_ED25519","created":"2019-06-14T13:53:20.000Z","hwDeviceId":"55424952-3c71-bf88-1fa4-3c71bf881fa4","pubKey":"6LFYOnlZEbpIIfbRWVf7sqi2WJ+sDijwRp8dXUZOFzk=","pubKeyId":"6LFYOnlZEbpIIfbRWVf7sqi2WJ+sDijwRp8dXUZOFzk=","validNotAfter":"2020-06-04T13:53:20.000Z","validNotBefore":"2019-06-14T13:53:20.000Z"},"signature":"fde03123a4a784a825ea879216d4186b4729aead7c649d94aa0db72964fe8b3d2a4cdf5b1adf432b9df2f8af69215378fe30b3e9c5e2be4d27efa03d85538c0f"}"""

      val bytes = loadFixture("src/main/resources/fixtures/7_MsgPackKeyService.mpack")
      post("/v1/pubkey/mpack", body = bytes) {
        body should equal(expectedBody)
        status should equal(200)
      }

      post("/v1/pubkey/mpack", body = bytes) {
        body should equal(expectedBody)
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

      PublicKeyCreationHelpers.getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore) match {
        case Right((_, pkAsString, _, _, _)) =>
          post("/v1/pubkey", body = pkAsString) {
            status should equal(200)
            body should equal(pkAsString)
          }
        case Left(e) =>
          fail(e)

      }
      PublicKeyCreationHelpers.getPublicKey(PublicKeyUtil.EDDSA, created, validNotAfter, created) match {
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
        res <- PublicKeyCreationHelpers.getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore)
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
      val hardwareDeviceId: String = UUID.randomUUID().toString

      (for {
        res1 <- PublicKeyCreationHelpers.getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore, hardwareDeviceId)
        (pk1, pkAsString1, _, _, pkr1) = res1

        signature1 <- Try(pkr1.sign(pkr1.getRawPublicKey)).toEither
        signatureAsString1 <- Try(Base64.getEncoder.encodeToString(signature1)).toEither
        pubDelete1 = PublicKeyDelete(pk1.pubKeyInfo.pubKeyId, signatureAsString1)
        pubDeleteAsString1 <- jsonConverter.toString[PublicKeyDelete](pubDelete1)

        res2 <- PublicKeyCreationHelpers.getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore, hardwareDeviceId)
        (pk2, _, _, _, pkr2) = res2

        signed2 <- PublicKeyCreationHelpers.sign(pk2.pubKeyInfo, pkr1)
        (_, signature2, _) = signed2

        pk2WithPrevSign = pk2.copy(prevSignature = Option(signature2))
        pk2WithPrevSignAsString <- jsonConverter.toString[PublicKey](pk2WithPrevSign)

        signature2 <- Try(pkr2.sign(pkr2.getRawPublicKey)).toEither
        signatureAsString2 <- Try(Base64.getEncoder.encodeToString(signature2)).toEither
        pubDelete2 = PublicKeyDelete(pk2WithPrevSign.pubKeyInfo.pubKeyId, signatureAsString2)
        pubDeleteAsString2 <- jsonConverter.toString[PublicKeyDelete](pubDelete2)

        res3 <- PublicKeyCreationHelpers.getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore, hardwareDeviceId)
        (pk3, _, _, _, pkr3) = res3

        signed3 <- PublicKeyCreationHelpers.sign(pk3.pubKeyInfo, pkr2)
        (_, signature3, _) = signed3

        pk3WithPrevSign = pk3.copy(prevSignature = Option(signature3))
        pk3WithPrevSignAsString <- jsonConverter.toString[PublicKey](pk3WithPrevSign)

        signature3 <- Try(pkr3.sign(pkr3.getRawPublicKey)).toEither
        signatureAsString3 <- Try(Base64.getEncoder.encodeToString(signature3)).toEither
        _ = PublicKeyDelete(pk3.pubKeyInfo.pubKeyId, signatureAsString3)

      } yield {

        post("/v1/pubkey", body = pkAsString1) {
          status should equal(200)
          body should equal(pkAsString1)
        }

        post("/v1/pubkey", body = pk2WithPrevSignAsString) {
          status should equal(200)
          body should equal(pk2WithPrevSignAsString)
        }

        post("/v1/pubkey", body = pk3WithPrevSignAsString) {
          status should equal(200)
          body should equal(pk3WithPrevSignAsString)
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
          body should equal(s"[$pk3WithPrevSignAsString]")
        }

      }).getOrElse(fail())

    }

    "wrong body as mpack" taggedAs Tag("cherimoya") in {
      post("/v1/pubkey/mpack", body = Array.empty) {
        assert(jsonConverter.as[NOK](body).isRight)
        status should equal(400)
      }
    }

    "wrong body as json" taggedAs Tag("cherimoya") in {
      post("/v1/pubkey", body = "") {
        assert(jsonConverter.as[NOK](body).isRight)
        status should equal(400)
      }
    }

    "create a second key when signed by the previous key -json-" taggedAs Tag("soursop") in {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-06-09T09:50:26.083Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"Zk8/Lb9UKdFI07rTxAqrqmlQfEZH9w+2lAAXWUPIUYk=","pubKeyId":"Zk8/Lb9UKdFI07rTxAqrqmlQfEZH9w+2lAAXWUPIUYk=","validNotAfter":"2020-12-09T09:50:26.083Z","validNotBefore":"2020-06-09T09:50:26.083Z"},"signature":"Pqi2Tfs9sFsoWKzfAkUK6RYl+IkisHNpLcFju9nOS7IMQ/pJW0PFlUorz+NeA2EZThSCUaCAmQoywA/nMGABAA=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-06-09T09:50:26.990Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"6MKCPw0iyAEBeW1qaK/qK+y86bzkiFb3SvC8uk3bIRk=","pubKeyId":"6MKCPw0iyAEBeW1qaK/qK+y86bzkiFb3SvC8uk3bIRk=","validNotAfter":"2020-12-09T09:50:26.990Z","validNotBefore":"2020-06-09T09:50:26.990Z"},"signature":"VcvUGzPBKsicQ2doqECSTrV+rmbJ992caBWwdJaKtu9sBtz2ukk8E7YlSELshYs1Slcw//GCVTXr8BjS5wl0DQ==","prevSignature":"JAwWDwkiNlpnNkzr7v3WSqgKXcG5j7XqFUU8N+8+F4Lk4T+vVd/4+uywoOzrpmlFnYlA+QbWciII81L2E/ZKDA=="}"""

      post("/v1/pubkey", body = dataKey2) {
        status should equal(200)
        body should equal(dataKey2)
      }

      val expectedKeys = """[{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-06-09T09:50:26.990Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"6MKCPw0iyAEBeW1qaK/qK+y86bzkiFb3SvC8uk3bIRk=","pubKeyId":"6MKCPw0iyAEBeW1qaK/qK+y86bzkiFb3SvC8uk3bIRk=","validNotAfter":"2020-12-09T09:50:26.990Z","validNotBefore":"2020-06-09T09:50:26.990Z"},"signature":"VcvUGzPBKsicQ2doqECSTrV+rmbJ992caBWwdJaKtu9sBtz2ukk8E7YlSELshYs1Slcw//GCVTXr8BjS5wl0DQ==","prevSignature":"JAwWDwkiNlpnNkzr7v3WSqgKXcG5j7XqFUU8N+8+F4Lk4T+vVd/4+uywoOzrpmlFnYlA+QbWciII81L2E/ZKDA=="},{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-06-09T09:50:26.083Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"Zk8/Lb9UKdFI07rTxAqrqmlQfEZH9w+2lAAXWUPIUYk=","pubKeyId":"Zk8/Lb9UKdFI07rTxAqrqmlQfEZH9w+2lAAXWUPIUYk=","validNotAfter":"2020-12-09T09:50:26.083Z","validNotBefore":"2020-06-09T09:50:26.083Z"},"signature":"Pqi2Tfs9sFsoWKzfAkUK6RYl+IkisHNpLcFju9nOS7IMQ/pJW0PFlUorz+NeA2EZThSCUaCAmQoywA/nMGABAA=="}]"""

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal(expectedKeys)
      }

    }

    "second key should not be created if not signed -json-" taggedAs Tag("sapote") in {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ecdsa-p256v1","created":"2020-05-22T12:52:02.892Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"eqWsIjD1OoaLgKSPHcPKagbjHzTLdvIIdWmR8UM8V09jzZQORV9xzvZRW249JerpufEhX135dFafETFT6jGwWA==","pubKeyId":"4318f9cf-e75b-4c43-a1c4-78741e6cd7c6","validNotAfter":"2020-11-22T12:52:02.892Z","validNotBefore":"2020-05-22T12:52:02.892Z"},"signature":"MEUCIQDhqpZscBu1hG0N3Qq+pQCoDinjhQWS3JBSehlyBCgzDwIgJiY05NKJf2brxx7ox/59xawFq3YM4hEx8aVLJzkd27Y="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-05-22T13:05:04.956Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"9p/qTu/+O9VVWS4TUbRtk5K21f5Z7J/aXPIIbM8Ng4A=","pubKeyId":"284bc3be-d649-4f7e-8a3b-439ac3292152","validNotAfter":"2020-11-22T13:05:04.956Z","validNotBefore":"2020-05-22T13:05:04.956Z"},"signature":"kM0WHPYDbiFXXPiXHXoFokFfwyaAlyUtWEaPxepSnDXLOexV04zlB2a86M1CpHAKveKeE9qoQgOqOjlmFzfjAQ=="}"""

      post("/v1/pubkey", body = dataKey2) {
        status should equal(400)
        body should equal("""{"version":"1.0","status":"NOK","errorType":"PubkeyError","errorMessage":"Error creating pub key"}""")
      }

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal("[" + dataKey1 + "]")
      }

    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
    cassandra.executeScripts(EmbeddedCassandra.scripts: _*)
  }

  protected override def afterAll(): Unit = {
    EmbeddedKafka.stop()
    cassandra.stop()
    super.afterAll()
  }

  protected override def beforeAll(): Unit = {

    CollectorRegistry.defaultRegistry.clear()
    EmbeddedKafka.start()
    cassandra.start()

    lazy val keyController = Injector.get[KeyController]

    addServlet(keyController, "/*")

    //cassandra.executeScripts(EmbeddedCassandra.scripts: _*)

    super.beforeAll()
  }
}
