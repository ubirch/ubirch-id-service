package com.ubirch.controllers

import java.util.{ Base64, UUID }

import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.{ NOK, PublicKey, PublicKeyDelete, PublicKeyRevoke }
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.util.{ DateUtil, PublicKeyCreationHelpers, PublicKeyUtil }
import com.ubirch.{ EmbeddedCassandra, _ }
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig }
import org.apache.commons.codec.binary.Hex
import org.scalatest.{ BeforeAndAfterEach, Tag }
import org.scalatra.test.scalatest.ScalatraWordSpec

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

  val cassandra = new CassandraTest

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

    //TODO: update fixtures
    "get public key object when data exists" taggedAs Tag("mango") ignore {

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

    //TODO: update fixtures
    "get public key object when data exists by hardware id " taggedAs Tag("cherry") ignore {

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

    //TODO: update fixtures
    "get public key object when data exists by hardware id when / is present" taggedAs Tag("lychee") ignore {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-14T12:37:49.592Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"M/+XARYp4NsBaRdaJ/Eugrcgvr0mLsjiUBJoI2Na78E=","pubKeyId":"M/+XARYp4NsBaRdaJ/Eugrcgvr0mLsjiUBJoI2Na78E=","validNotAfter":"2021-01-14T12:37:49.592Z","validNotBefore":"2020-07-14T12:37:49.592Z"},"signature":"HDdQvZh43YaPEEfZYwAjNyuL+55RsS8D1r97uaif8uTykmpjqOMjUoDLotCwRxai0vCajYKnfFM+5y7xgEkKDw=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal("[" + dataKey1 + "]")
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-14T12:37:51.087Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"JoyQ2qtnHgaZ4G12P4WujjgtrmkiUI3l636p5gs2vlo=","pubKeyId":"JoyQ2qtnHgaZ4G12P4WujjgtrmkiUI3l636p5gs2vlo=","prevPubKeyId":"M/+XARYp4NsBaRdaJ/Eugrcgvr0mLsjiUBJoI2Na78E=","validNotAfter":"2021-01-14T12:37:51.087Z","validNotBefore":"2020-07-14T12:37:51.087Z"},"signature":"fgRNKLxrf7CN1CCjPsDFIKfu3rrCzdlQTx4BllBrjk9zJrYABO/zPS+GiEnGKFmG2csVvSX5OiuL/+Fcvu4yCw==","prevSignature":"3PjuBswPKGcJ7UC7dKLvleDrMHnBG5JPZBIuNJlWdAX1e+MwP1Eu9nBNR/aLVe4B40pr5rcTu/NqjXwwK6izAA=="}"""

      post("/v1/pubkey", body = dataKey2) {
        status should equal(200)
        body should equal(dataKey2)
      }

      val expectedKeys = """[{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-14T12:37:51.087Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"JoyQ2qtnHgaZ4G12P4WujjgtrmkiUI3l636p5gs2vlo=","pubKeyId":"JoyQ2qtnHgaZ4G12P4WujjgtrmkiUI3l636p5gs2vlo=","prevPubKeyId":"M/+XARYp4NsBaRdaJ/Eugrcgvr0mLsjiUBJoI2Na78E=","validNotAfter":"2021-01-14T12:37:51.087Z","validNotBefore":"2020-07-14T12:37:51.087Z"},"signature":"fgRNKLxrf7CN1CCjPsDFIKfu3rrCzdlQTx4BllBrjk9zJrYABO/zPS+GiEnGKFmG2csVvSX5OiuL/+Fcvu4yCw==","prevSignature":"3PjuBswPKGcJ7UC7dKLvleDrMHnBG5JPZBIuNJlWdAX1e+MwP1Eu9nBNR/aLVe4B40pr5rcTu/NqjXwwK6izAA=="},{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-14T12:37:49.592Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"M/+XARYp4NsBaRdaJ/Eugrcgvr0mLsjiUBJoI2Na78E=","pubKeyId":"M/+XARYp4NsBaRdaJ/Eugrcgvr0mLsjiUBJoI2Na78E=","validNotAfter":"2021-01-14T12:37:49.592Z","validNotBefore":"2020-07-14T12:37:49.592Z"},"signature":"HDdQvZh43YaPEEfZYwAjNyuL+55RsS8D1r97uaif8uTykmpjqOMjUoDLotCwRxai0vCajYKnfFM+5y7xgEkKDw=="}]""".stripMargin

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal(expectedKeys)
      }

    }

    //TODO: update fixtures
    "create when signed by previous" taggedAs Tag("breadfruit") ignore {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-27T15:20:31.527Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"8HnM8Cc7Az2zEkyMZy+OxNgis4miNxF6aOYxKRkOqtw=","pubKeyId":"8HnM8Cc7Az2zEkyMZy+OxNgis4miNxF6aOYxKRkOqtw=","validNotAfter":"2021-01-27T15:20:31.527Z","validNotBefore":"2020-07-27T15:20:31.527Z"},"signature":"drlkRqoP+A5kz08tp+MvCf4OiSJ6v5cA5LAV5/m0JFJgceUo2NtMRc0Voi5p1LPJqt4gHNSq2chG0gtcIojWDg=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal("[" + dataKey1 + "]")
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-27T15:20:32.966Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"CxvssKUuiJLab+EgpfHesVqxE7JUbm7uxXB8h7uTdCE=","pubKeyId":"CxvssKUuiJLab+EgpfHesVqxE7JUbm7uxXB8h7uTdCE=","prevPubKeyId":"8HnM8Cc7Az2zEkyMZy+OxNgis4miNxF6aOYxKRkOqtw=","validNotAfter":"2021-01-27T15:20:32.966Z","validNotBefore":"2020-07-27T15:20:32.966Z"},"signature":"Iv1Bh3eUAQlRKVw9iFcYqX42JT/1prEqwQzwytKv0vmhvHjpqbaSlcB+AvDLu1+eAvGeFTz9rN4Z4x9RLiVBDQ==","prevSignature":"NILz1+VHpJP5xutrS5llPLXv+MhYmgU4/0B1W1wvCVzzJu22cZpEcwDvMkJHnwMrn6pqjyrkst9OWd7nien1AA=="}"""

      post("/v1/pubkey", body = dataKey2) {
        status should equal(200)
        body should equal(dataKey2)
      }

      val dataKey3 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-27T15:20:32.996Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"HlbuIVf920Zp4sARsymxcPLiDaTsUdpDtgx8PzIaQSc=","pubKeyId":"HlbuIVf920Zp4sARsymxcPLiDaTsUdpDtgx8PzIaQSc=","prevPubKeyId":"CxvssKUuiJLab+EgpfHesVqxE7JUbm7uxXB8h7uTdCE=","validNotAfter":"2021-01-27T15:20:32.996Z","validNotBefore":"2020-07-27T15:20:32.996Z"},"signature":"Tl1PWCokKANtzZg8H82n6oev2432EBDAz5Tas0OmZtuAz7GPlrbY8M2SDVf85flOHvQetc4hKx5L2v9rvDdOAA==","prevSignature":"FPl9rPJABABpF80QNSrZbhiDavtIm9RK7721gwSSm2G13xoswJ7e1tjvx4oIS31kNDaQr3z4j61AIgk36jp3Cg=="}"""

      post("/v1/pubkey", body = dataKey3) {
        status should equal(200)
        body should equal(dataKey3)
      }

      val dataKey4 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-27T15:21:41.499Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"qhMLEsHjd/Dxsgp0bahwf9plmAeruN7D3i5GcTpmAOo=","pubKeyId":"qhMLEsHjd/Dxsgp0bahwf9plmAeruN7D3i5GcTpmAOo=","validNotAfter":"2021-01-27T15:21:41.499Z","validNotBefore":"2020-07-27T15:21:41.499Z"},"signature":"nixHHeuueYuehNwHw9r0ye1/3HG2nej2kIHb+hIgyPd9lQ4+glwoENKT7WrNrPaVClsw3qsch8vszQBJa8NLCw=="}""".stripMargin

      post("/v1/pubkey", body = dataKey4) {
        status should equal(400)
        body should equal("""{"version":"1.0","status":"NOK","errorType":"PubkeyError","errorMessage":"Error creating pub key"}""")
      }

      val expectedKeys = """[{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-27T15:20:32.966Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"CxvssKUuiJLab+EgpfHesVqxE7JUbm7uxXB8h7uTdCE=","pubKeyId":"CxvssKUuiJLab+EgpfHesVqxE7JUbm7uxXB8h7uTdCE=","prevPubKeyId":"8HnM8Cc7Az2zEkyMZy+OxNgis4miNxF6aOYxKRkOqtw=","validNotAfter":"2021-01-27T15:20:32.966Z","validNotBefore":"2020-07-27T15:20:32.966Z"},"signature":"Iv1Bh3eUAQlRKVw9iFcYqX42JT/1prEqwQzwytKv0vmhvHjpqbaSlcB+AvDLu1+eAvGeFTz9rN4Z4x9RLiVBDQ==","prevSignature":"NILz1+VHpJP5xutrS5llPLXv+MhYmgU4/0B1W1wvCVzzJu22cZpEcwDvMkJHnwMrn6pqjyrkst9OWd7nien1AA=="},{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-27T15:20:32.996Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"HlbuIVf920Zp4sARsymxcPLiDaTsUdpDtgx8PzIaQSc=","pubKeyId":"HlbuIVf920Zp4sARsymxcPLiDaTsUdpDtgx8PzIaQSc=","prevPubKeyId":"CxvssKUuiJLab+EgpfHesVqxE7JUbm7uxXB8h7uTdCE=","validNotAfter":"2021-01-27T15:20:32.996Z","validNotBefore":"2020-07-27T15:20:32.996Z"},"signature":"Tl1PWCokKANtzZg8H82n6oev2432EBDAz5Tas0OmZtuAz7GPlrbY8M2SDVf85flOHvQetc4hKx5L2v9rvDdOAA==","prevSignature":"FPl9rPJABABpF80QNSrZbhiDavtIm9RK7721gwSSm2G13xoswJ7e1tjvx4oIS31kNDaQr3z4j61AIgk36jp3Cg=="},{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-27T15:20:31.527Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"8HnM8Cc7Az2zEkyMZy+OxNgis4miNxF6aOYxKRkOqtw=","pubKeyId":"8HnM8Cc7Az2zEkyMZy+OxNgis4miNxF6aOYxKRkOqtw=","validNotAfter":"2021-01-27T15:20:31.527Z","validNotBefore":"2020-07-27T15:20:31.527Z"},"signature":"drlkRqoP+A5kz08tp+MvCf4OiSJ6v5cA5LAV5/m0JFJgceUo2NtMRc0Voi5p1LPJqt4gHNSq2chG0gtcIojWDg=="}]""".stripMargin

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal(expectedKeys)
      }

    }

    "get public key object when data exists by public key id when / is present" taggedAs Tag("pummelo") in {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-11-28T10:02:17.253Z","hwDeviceId":"31a12f36-61a4-4337-827b-ca5655b16c53","pubKey":"x3FSPYIrYQ1qSegVW4IJF7j1qWf8z3NupfeedJtK1Js=","pubKeyId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","validNotAfter":"2021-05-28T10:02:17.253Z","validNotBefore":"2020-11-28T10:02:17.253Z"},"signature":"xyZDIjOH4UE7FRvh3TNXks/f/X7q9zkVWEL9Bfb8LLsX+MDlqTw4vr+4hf5sMFMbF7sYDQfM5VvZ9rGHiXheBA=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      get("/v1/pubkey/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal(dataKey1)
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-11-28T10:04:51.905Z","hwDeviceId":"32cc20d7-a565-43e4-b0a2-bc43eaa67c16","pubKey":"lccN/yyor3bI8igjLWq73c9Fw983rAj0d2udW4kkqY8=","pubKeyId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","validNotAfter":"2021-05-28T10:04:51.905Z","validNotBefore":"2020-11-28T10:04:51.905Z"},"signature":"et8w6+uI/fpjUVhITUuQ/9tA2GW9nHmSSedIumXvKXqwlzmCYAN+mNIt8roifZf8poW/Aa3hlUVMsz+Qf3yHCg=="}"""

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

    "create key using the json endpoint when same key" taggedAs Tag("mulberries") in {

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

      Thread.sleep(3000)

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

    "create key using the mpack endpoint from trackle message from hex" taggedAs Tag("date_fruit") in {

      val bytes1 = Hex.decodeHex("95cd0012b04746b40290a3182f4aab5f14da0166f20186a9616c676f726974686dab4543435f45443235353139a763726561746564ce5f23f255aa68774465766963654964b04746b40290a3182f4aab5f14da0166f2a67075624b6579da0020c8904a680a964ca7a35fe308e55042019b3c8638698b320253bc1363d5494851ad76616c69644e6f744166746572ce62e65955ae76616c69644e6f744265666f7265ce5f23f255da00404d5512cb93ebc3a09170f3bde5530c90942a009d076f90854265c2d9a27494ea5c95804c47d11eb08350b36dead1383981cc65177ca5699d00b601741bcf540c")

      post("/v1/pubkey/mpack", body = bytes1) {
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

        res2 <- PublicKeyCreationHelpers.getPublicKey(
          curveName = PublicKeyUtil.ECDSA,
          created = created,
          validNotAfter = validNotAfter,
          validNotBefore = validNotBefore,
          hardwareDeviceId = hardwareDeviceId,
          prevPubKeyId = Some(pk1.pubKeyInfo.pubKeyId)
        )
        (pk2, _, _, _, pkr2) = res2

        signed2 <- PublicKeyCreationHelpers.sign(pk2.pubKeyInfo, pkr1)
        (_, signature2, _) = signed2

        pk2WithPrevSign = pk2.copy(prevSignature = Option(signature2))
        pk2WithPrevSignAsString <- jsonConverter.toString[PublicKey](pk2WithPrevSign)

        signature2 <- Try(pkr2.sign(pkr2.getRawPublicKey)).toEither
        signatureAsString2 <- Try(Base64.getEncoder.encodeToString(signature2)).toEither
        pubDelete2 = PublicKeyDelete(pk2WithPrevSign.pubKeyInfo.pubKeyId, signatureAsString2)
        pubDeleteAsString2 <- jsonConverter.toString[PublicKeyDelete](pubDelete2)

        res3 <- PublicKeyCreationHelpers.getPublicKey(
          curveName = PublicKeyUtil.ECDSA,
          created = created,
          validNotAfter = validNotAfter,
          validNotBefore = validNotBefore,
          hardwareDeviceId = hardwareDeviceId,
          prevPubKeyId = Some(pk2.pubKeyInfo.pubKeyId)
        )
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

    "wrong body as mpack 2" taggedAs Tag("cherimoya") in {
      post("/v1/pubkey/mpack", body = Array(0)) {
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

    "wrong body as json 2 " taggedAs Tag("cherimoya") in {
      val dataKey1 = """{"pubeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-06-09T09:50:26.083Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"Zk8/Lb9UKdFI07rTxAqrqmlQfEZH9w+2lAAXWUPIUYk=","pubKeyId":"Zk8/Lb9UKdFI07rTxAqrqmlQfEZH9w+2lAAXWUPIUYk=","validNotAfter":"2020-12-09T09:50:26.083Z","validNotBefore":"2020-06-09T09:50:26.083Z"},"signature":"Pqi2Tfs9sFsoWKzfAkUK6RYl+IkisHNpLcFju9nOS7IMQ/pJW0PFlUorz+NeA2EZThSCUaCAmQoywA/nMGABAA=="}""".stripMargin
      post("/v1/pubkey", body = dataKey1) {
        assert(jsonConverter.as[NOK](body).isRight)
        status should equal(400)
      }
    }

    //TODO: update fixtures
    "create a second key when signed by the previous key -json-" taggedAs Tag("soursop") ignore {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-14T13:07:11.793Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"1g+pL87spzPirEN223IicvBXCVo4VMtOrXNLFBJMrus=","pubKeyId":"1g+pL87spzPirEN223IicvBXCVo4VMtOrXNLFBJMrus=","validNotAfter":"2021-01-14T13:07:11.793Z","validNotBefore":"2020-07-14T13:07:11.793Z"},"signature":"z83MCPDeyXvC9pMVLlWzrqnYiv9/31i+cEnim0oPKN05mqHfq56Ef+1x3VeWLun9DcVy344BZMEsDQ+DY1biDQ=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-14T13:07:12.584Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"HpVU+CDweEhEqajOfyBltAMg8XkfrUjlcZAddQcf5TA=","pubKeyId":"HpVU+CDweEhEqajOfyBltAMg8XkfrUjlcZAddQcf5TA=","prevPubKeyId":"1g+pL87spzPirEN223IicvBXCVo4VMtOrXNLFBJMrus=","validNotAfter":"2021-01-14T13:07:12.584Z","validNotBefore":"2020-07-14T13:07:12.584Z"},"signature":"SgrLuxboucGeZ4MMUh37ekW3WV/zK9+nmm4tShkpcCVwc3VbXIPXnaZ1lUX+vz0pWrfKvinAIahEGp0C7QXxAw==","prevSignature":"90NTvcpEctUrZTXmw6it6GNKwKHpWeViOt3oOHXJYoOxonkunWagcdDEdI4FxeZEfOht0JiDWrUF3pccwx+3Bg=="}"""

      post("/v1/pubkey", body = dataKey2) {
        status should equal(200)
        body should equal(dataKey2)
      }

      //This key is not connected to any existing key
      val dataKey3 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-14T13:12:50.691Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"oZn3G+F8AtL55el2IU1aeY/wGQzKQGiHKFdTydhau3g=","pubKeyId":"oZn3G+F8AtL55el2IU1aeY/wGQzKQGiHKFdTydhau3g=","prevPubKeyId":"BKP2hedGpWPyqjMcoTPb33YoE639nDpoKJkcZlCtqWA=","validNotAfter":"2021-01-14T13:12:50.691Z","validNotBefore":"2020-07-14T13:12:50.691Z"},"signature":"get9v2IXCcGmeqQRuPHpC+twzuesoE3EkzEcw3p4G1/py+BPdmgUbtg38sBQNzJ3EJZEu18WGMc03cZNCFofAQ==","prevSignature":"W5fiqTytjAFg1Y+yHIXgGFCg9gil9392505MWkz6s4M4tQ55NCvyHI+wFGyBfAn8GAZMOseR0de/Hj8FV+EuCw=="}"""

      post("/v1/pubkey", body = dataKey3) {
        status should equal(400)
        body should equal("""{"version":"1.0","status":"NOK","errorType":"PubkeyError","errorMessage":"Error creating pub key"}""")
      }

      val expectedKeys = """[{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-14T13:07:12.584Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"HpVU+CDweEhEqajOfyBltAMg8XkfrUjlcZAddQcf5TA=","pubKeyId":"HpVU+CDweEhEqajOfyBltAMg8XkfrUjlcZAddQcf5TA=","prevPubKeyId":"1g+pL87spzPirEN223IicvBXCVo4VMtOrXNLFBJMrus=","validNotAfter":"2021-01-14T13:07:12.584Z","validNotBefore":"2020-07-14T13:07:12.584Z"},"signature":"SgrLuxboucGeZ4MMUh37ekW3WV/zK9+nmm4tShkpcCVwc3VbXIPXnaZ1lUX+vz0pWrfKvinAIahEGp0C7QXxAw==","prevSignature":"90NTvcpEctUrZTXmw6it6GNKwKHpWeViOt3oOHXJYoOxonkunWagcdDEdI4FxeZEfOht0JiDWrUF3pccwx+3Bg=="},{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-07-14T13:07:11.793Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"1g+pL87spzPirEN223IicvBXCVo4VMtOrXNLFBJMrus=","pubKeyId":"1g+pL87spzPirEN223IicvBXCVo4VMtOrXNLFBJMrus=","validNotAfter":"2021-01-14T13:07:11.793Z","validNotBefore":"2020-07-14T13:07:11.793Z"},"signature":"z83MCPDeyXvC9pMVLlWzrqnYiv9/31i+cEnim0oPKN05mqHfq56Ef+1x3VeWLun9DcVy344BZMEsDQ+DY1biDQ=="}]""".stripMargin

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal(expectedKeys)
      }

    }

    "second key should not be created if not signed -json-" taggedAs Tag("sapote") in {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-11-28T10:14:48.200Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"SnQTBQ1DMCfIu8tWzPvo9cIY5yoFLvaalo4+KNj2P3k=","pubKeyId":"SnQTBQ1DMCfIu8tWzPvo9cIY5yoFLvaalo4+KNj2P3k=","validNotAfter":"2021-05-28T10:14:48.200Z","validNotBefore":"2020-11-28T10:14:48.200Z"},"signature":"uqYVotqMVjpLHxM17mD1ojSG1xOdiZ+J2xiVHSPn3bzVRzdRugQJxJjyMCMcuEDuKmh0ibGYjjzTgCVxEJVlAA=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2020-11-28T10:16:14.416Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"7++b+hdMoY5FK+eanAzzSeAy4gsIuk9RQPvHmjr0BrQ=","pubKeyId":"7++b+hdMoY5FK+eanAzzSeAy4gsIuk9RQPvHmjr0BrQ=","validNotAfter":"2021-05-28T10:16:14.416Z","validNotBefore":"2020-11-28T10:16:14.416Z"},"signature":"02rfLZBPmgg7s0ZV8Cum+oa+3usZn9Y1SufJI0K4ARVOVq6ieYi2CkEdbPlaN30RtbHOBJSmO+Dp5df3HuY9BA=="}"""

      post("/v1/pubkey", body = dataKey2) {
        status should equal(400)
        body should equal("""{"version":"1.0","status":"NOK","errorType":"PubkeyError","errorMessage":"Error creating pub key"}""")
      }

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal("[" + dataKey1 + "]")
      }

    }

    "revoke key" taggedAs Tag("carrot") in {

      val created = DateUtil.nowUTC
      val validNotAfter = Some(created.plusMonths(6))
      val validNotBefore = created
      val hardwareId = UUID.randomUUID()

      (for {
        res <- PublicKeyCreationHelpers.getPublicKey(PublicKeyUtil.ECDSA, created, validNotAfter, validNotBefore, hardwareDeviceId = hardwareId.toString)
        (pk, pkAsString, _, _, pkr) = res
        signature <- Try(pkr.sign(pkr.getRawPublicKey)).toEither
        signatureAsString <- Try(Base64.getEncoder.encodeToString(signature)).toEither
        pubRevoke = PublicKeyRevoke(pk.pubKeyInfo.pubKeyId, signatureAsString)
        pubRevokeAsString <- jsonConverter.toString[PublicKeyRevoke](pubRevoke)
        pubDelete = PublicKeyDelete(pk.pubKeyInfo.pubKeyId, signatureAsString)
        pubDeleteAsString <- jsonConverter.toString[PublicKeyDelete](pubDelete)

      } yield {

        post("/v1/pubkey", body = pkAsString) {
          status should equal(200)
          body should equal(pkAsString)
        }

        get("/v1/pubkey/current/hardwareId/" + hardwareId) {
          status should equal(200)
          body should equal("[" + pkAsString + "]")
        }

        get("/v1/pubkey/" + pk.pubKeyInfo.pubKeyId) {
          status should equal(200)
          body should equal(pkAsString)
        }

        patch("/v1/pubkey/revoke", body = pubRevokeAsString) {
          status should equal(200)
          body should equal("""{"version":"1.0","status":"OK","message":"Key revoked"}""")
        }

        get("/v1/pubkey/current/hardwareId/" + hardwareId) {
          status should equal(200)
          body should equal("[]")
        }

        get("/v1/pubkey/" + pk.pubKeyInfo.pubKeyId) {
          status should equal(404)
          body should equal("""{"version":"1.0","status":"NOK","errorType":"PubkeyError","errorMessage":"Key not found"}""")
        }

        post("/v1/pubkey", body = pkAsString) {
          status should equal(400)
          body should equal("""{"version":"1.0","status":"NOK","errorType":"PubkeyError","errorMessage":"Error creating pub key"}""")
        }

        patch("/v1/pubkey", body = pubDeleteAsString) {
          status should equal(200)
          body should equal("""{"version":"1.0","status":"OK","message":"Key deleted"}""")
        }

      }).getOrElse(fail())

    }

    "create subsequent key not possible after key has been revoked" taggedAs Tag("corn") in {

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

        res2 <- PublicKeyCreationHelpers.getPublicKey(
          curveName = PublicKeyUtil.ECDSA,
          created = created,
          validNotAfter = validNotAfter,
          validNotBefore = validNotBefore,
          hardwareDeviceId = hardwareDeviceId,
          prevPubKeyId = Some(pk1.pubKeyInfo.pubKeyId)
        )
        (pk2, _, _, _, pkr2) = res2

        signed2 <- PublicKeyCreationHelpers.sign(pk2.pubKeyInfo, pkr1)
        (_, signature2, _) = signed2

        pk2WithPrevSign = pk2.copy(prevSignature = Option(signature2))
        pk2WithPrevSignAsString <- jsonConverter.toString[PublicKey](pk2WithPrevSign)

        signature2 <- Try(pkr2.sign(pkr2.getRawPublicKey)).toEither
        signatureAsString2 <- Try(Base64.getEncoder.encodeToString(signature2)).toEither
        pubDelete2 = PublicKeyDelete(pk2WithPrevSign.pubKeyInfo.pubKeyId, signatureAsString2)
        pubDeleteAsString2 <- jsonConverter.toString[PublicKeyDelete](pubDelete2)

        res3 <- PublicKeyCreationHelpers.getPublicKey(
          curveName = PublicKeyUtil.ECDSA,
          created = created,
          validNotAfter = validNotAfter,
          validNotBefore = validNotBefore,
          hardwareDeviceId = hardwareDeviceId,
          prevPubKeyId = Some(pk2.pubKeyInfo.pubKeyId)
        )
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

        patch("/v1/pubkey/revoke", body = pubDeleteAsString1) {
          status should equal(200)
          body should equal("""{"version":"1.0","status":"OK","message":"Key revoked"}""")
        }

        post("/v1/pubkey", body = pk2WithPrevSignAsString) {
          status should equal(400)
          body should equal("""{"version":"1.0","status":"NOK","errorType":"PubkeyError","errorMessage":"Error creating pub key"}""")
        }

      }).getOrElse(fail())

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

    CollectorRegistry.defaultRegistry.clear()
    EmbeddedKafka.start()
    cassandra.startAndCreateDefaults()

    lazy val keyController = Injector.get[KeyController]

    addServlet(keyController, "/*")

    super.beforeAll()
  }
}
