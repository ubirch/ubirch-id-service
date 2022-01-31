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

    "get public key object when data exists" taggedAs Tag("mango") in {

      val expectedBody = """{"pubKeyInfo":{"algorithm":"ECC_ED25519","created":"2022-01-31T09:59:08.042Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"iHnu16KSvS5Trzv2DU7hmAtcRYQqYFF/w3LLAp7Dnxc=","pubKeyId":"iHnu16KSvS5Trzv2DU7hmAtcRYQqYFF/w3LLAp7Dnxc=","validNotAfter":"2032-01-31T09:59:08.042Z","validNotBefore":"2022-01-31T09:59:08.042Z"},"signature":"12i1M3vIxUPOQ4ZGusvG1wtj9O2r3QBQ8ozRvX/g4NWzp2wOCx7g8fOzPwP4BBRBIH7XA+JTN8e3WURqJWlvCA=="}""".stripMargin

      post("/v1/pubkey", body = expectedBody) {
        status should equal(200)
        body should equal(expectedBody)
      }

      get("/v1/pubkey/iHnu16KSvS5Trzv2DU7hmAtcRYQqYFF/w3LLAp7Dnxc=") {
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

      val expectedBody = """{"pubKeyInfo":{"algorithm":"ECC_ED25519","created":"2022-01-31T10:02:52.393Z","hwDeviceId":"e686b4ba-26b4-4a6d-8b57-f904299d4a5e","pubKey":"COu0qwHd7RfFDyZlXc1872s/+wPDJjG8Uox0IRZnyz4=","pubKeyId":"COu0qwHd7RfFDyZlXc1872s/+wPDJjG8Uox0IRZnyz4=","validNotAfter":"2032-01-31T10:02:52.393Z","validNotBefore":"2022-01-31T10:02:52.393Z"},"signature":"GQ56zN+XHG1VlzY5mZl4OULljQvvqs/oA4etWjbWpTqzCfj0PWZcTQOC8XcnCFlVGM4kujN25W6tiK33L++LCA=="}""".stripMargin

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

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T10:08:07.372Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"uYebuawx5Q4oJtm1OvULJMQg2vIZ7bvb9kdvsoFh+TY=","pubKeyId":"uYebuawx5Q4oJtm1OvULJMQg2vIZ7bvb9kdvsoFh+TY=","validNotAfter":"2032-01-31T10:08:07.372Z","validNotBefore":"2022-01-31T10:08:07.372Z"},"signature":"+1VmRyGOnl/oXGA1gaGgAOU1JFcWnRUio+1hnd8prZIawzP7FRwGjvlkTRCVbIpPVrwmF4xoYD/b6Je5PHz9AQ=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal("[" + dataKey1 + "]")
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T10:08:15.484Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"exB+duCgXG2Kj+J+Lp9+PGEIFUGVJDpN6v/OvVu9Dhw=","pubKeyId":"exB+duCgXG2Kj+J+Lp9+PGEIFUGVJDpN6v/OvVu9Dhw=","prevPubKeyId":"uYebuawx5Q4oJtm1OvULJMQg2vIZ7bvb9kdvsoFh+TY=","validNotAfter":"2032-01-31T10:08:15.484Z","validNotBefore":"2022-01-31T10:08:15.484Z"},"signature":"R445lLGA8AyfWF62+cirXXpWwfmlIVf+1DvbHpVpOeVd2U+vKdIYAeVZYMU5YhrsqHySS/i4J6Sn93VFKO4QDQ==","prevSignature":"gWEIFztLgWr47R55ml50iRXkA4L56FwwlvP4ZMdzdGzDKFK0mJpPdO5ab8MS3OT0RVa23eRZQZIUNrdwagUpCw=="}"""

      post("/v1/pubkey", body = dataKey2) {
        status should equal(200)
        body should equal(dataKey2)
      }

      val expectedKeys = """[{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T10:08:15.484Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"exB+duCgXG2Kj+J+Lp9+PGEIFUGVJDpN6v/OvVu9Dhw=","pubKeyId":"exB+duCgXG2Kj+J+Lp9+PGEIFUGVJDpN6v/OvVu9Dhw=","prevPubKeyId":"uYebuawx5Q4oJtm1OvULJMQg2vIZ7bvb9kdvsoFh+TY=","validNotAfter":"2032-01-31T10:08:15.484Z","validNotBefore":"2022-01-31T10:08:15.484Z"},"signature":"R445lLGA8AyfWF62+cirXXpWwfmlIVf+1DvbHpVpOeVd2U+vKdIYAeVZYMU5YhrsqHySS/i4J6Sn93VFKO4QDQ==","prevSignature":"gWEIFztLgWr47R55ml50iRXkA4L56FwwlvP4ZMdzdGzDKFK0mJpPdO5ab8MS3OT0RVa23eRZQZIUNrdwagUpCw=="},{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T10:08:07.372Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"uYebuawx5Q4oJtm1OvULJMQg2vIZ7bvb9kdvsoFh+TY=","pubKeyId":"uYebuawx5Q4oJtm1OvULJMQg2vIZ7bvb9kdvsoFh+TY=","validNotAfter":"2032-01-31T10:08:07.372Z","validNotBefore":"2022-01-31T10:08:07.372Z"},"signature":"+1VmRyGOnl/oXGA1gaGgAOU1JFcWnRUio+1hnd8prZIawzP7FRwGjvlkTRCVbIpPVrwmF4xoYD/b6Je5PHz9AQ=="}]""".stripMargin

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal(expectedKeys)
      }

    }

    "create when signed by previous" taggedAs Tag("breadfruit") in {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T10:25:56.902Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"l6KTnhPdl2/A9zY1kDMbELuwC/il2qMmpZGnnzvygo4=","pubKeyId":"l6KTnhPdl2/A9zY1kDMbELuwC/il2qMmpZGnnzvygo4=","validNotAfter":"2032-01-31T10:25:56.902Z","validNotBefore":"2022-01-31T10:25:56.902Z"},"signature":"Zx+dyuNFkPExZCMi0AGq0AP2rbXo/vOKDQQqwbjJuokZ4YebeSNDBSYFhtnc0U8iQnr1z8FuGlMpN5N75jPJCw=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal("[" + dataKey1 + "]")
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T10:26:01.502Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"670cMFXksdFIZaiQ+GFtZ7G5gMBfVEW6p/M7T4XL0Tg=","pubKeyId":"670cMFXksdFIZaiQ+GFtZ7G5gMBfVEW6p/M7T4XL0Tg=","prevPubKeyId":"l6KTnhPdl2/A9zY1kDMbELuwC/il2qMmpZGnnzvygo4=","validNotAfter":"2032-01-31T10:26:01.502Z","validNotBefore":"2022-01-31T10:26:01.502Z"},"signature":"RvmoFHtdSUTGyZxVm5wd9DS2bbMoM92AfuqbuLOxZqJ9b1/T+6FCTWBkgCG88x7eYQo5XMGerxkU/an96GdICw==","prevSignature":"rXJl5WOu5G7tt0e6sZLUeM6z46mHkByJsMBR/GgbkCEEOIs7UnK2Ir76X7C00Pjr/X6VeV5zuURIRTFB8J7rAg=="}"""

      post("/v1/pubkey", body = dataKey2) {
        status should equal(200)
        body should equal(dataKey2)
      }

      val dataKey3 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T10:26:01.554Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"lgGWoH2czQR1cDWud4SiUGGycamrPHGM6XU07a4iokA=","pubKeyId":"lgGWoH2czQR1cDWud4SiUGGycamrPHGM6XU07a4iokA=","prevPubKeyId":"670cMFXksdFIZaiQ+GFtZ7G5gMBfVEW6p/M7T4XL0Tg=","validNotAfter":"2032-01-31T10:26:01.554Z","validNotBefore":"2022-01-31T10:26:01.554Z"},"signature":"HBlTdUNaRgc8eOJ6AEuPaNBLDdAwj6r5y6u6Mjl7lo1FlmNU2jIgjZStY4oxLyqJPJdPad6h6W4BdyNTesVgCw==","prevSignature":"rgP/LSfU2rEbho1iDzXVpfsKbBaBp+owjV73I5YOezZqzvUAxXZ0cGO6sCjxpJxuFhXl5GxbzdArMjLSHBxhDw=="}"""

      post("/v1/pubkey", body = dataKey3) {
        status should equal(200)
        body should equal(dataKey3)
      }

      val dataKey4 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T10:28:00.595Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"T5vv8pGF5AEk5H+KUqk/b0Dz3TDska4vfrgN9lUCp2s=","pubKeyId":"T5vv8pGF5AEk5H+KUqk/b0Dz3TDska4vfrgN9lUCp2s=","validNotAfter":"2032-01-31T10:28:00.595Z","validNotBefore":"2022-01-31T10:28:00.595Z"},"signature":"Ig9qSXWnrx0170i0qUO56G7e5gX8rTNiJX4iNyl9TSD6gzQcUV4Db/mIlooPo9dy5QVaPADaSc6DhP4fQrJ+DA=="}""".stripMargin

      post("/v1/pubkey", body = dataKey4) {
        status should equal(400)
        body should equal("""{"version":"1.0","status":"NOK","errorType":"PubkeyError","errorMessage":"Error creating pub key"}""")
      }

      val expectedKeys = """[{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T10:26:01.502Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"670cMFXksdFIZaiQ+GFtZ7G5gMBfVEW6p/M7T4XL0Tg=","pubKeyId":"670cMFXksdFIZaiQ+GFtZ7G5gMBfVEW6p/M7T4XL0Tg=","prevPubKeyId":"l6KTnhPdl2/A9zY1kDMbELuwC/il2qMmpZGnnzvygo4=","validNotAfter":"2032-01-31T10:26:01.502Z","validNotBefore":"2022-01-31T10:26:01.502Z"},"signature":"RvmoFHtdSUTGyZxVm5wd9DS2bbMoM92AfuqbuLOxZqJ9b1/T+6FCTWBkgCG88x7eYQo5XMGerxkU/an96GdICw==","prevSignature":"rXJl5WOu5G7tt0e6sZLUeM6z46mHkByJsMBR/GgbkCEEOIs7UnK2Ir76X7C00Pjr/X6VeV5zuURIRTFB8J7rAg=="},{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T10:26:01.554Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"lgGWoH2czQR1cDWud4SiUGGycamrPHGM6XU07a4iokA=","pubKeyId":"lgGWoH2czQR1cDWud4SiUGGycamrPHGM6XU07a4iokA=","prevPubKeyId":"670cMFXksdFIZaiQ+GFtZ7G5gMBfVEW6p/M7T4XL0Tg=","validNotAfter":"2032-01-31T10:26:01.554Z","validNotBefore":"2022-01-31T10:26:01.554Z"},"signature":"HBlTdUNaRgc8eOJ6AEuPaNBLDdAwj6r5y6u6Mjl7lo1FlmNU2jIgjZStY4oxLyqJPJdPad6h6W4BdyNTesVgCw==","prevSignature":"rgP/LSfU2rEbho1iDzXVpfsKbBaBp+owjV73I5YOezZqzvUAxXZ0cGO6sCjxpJxuFhXl5GxbzdArMjLSHBxhDw=="},{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T10:25:56.902Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"l6KTnhPdl2/A9zY1kDMbELuwC/il2qMmpZGnnzvygo4=","pubKeyId":"l6KTnhPdl2/A9zY1kDMbELuwC/il2qMmpZGnnzvygo4=","validNotAfter":"2032-01-31T10:25:56.902Z","validNotBefore":"2022-01-31T10:25:56.902Z"},"signature":"Zx+dyuNFkPExZCMi0AGq0AP2rbXo/vOKDQQqwbjJuokZ4YebeSNDBSYFhtnc0U8iQnr1z8FuGlMpN5N75jPJCw=="}]""".stripMargin

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal(expectedKeys)
      }

    }

    "get public key object when data exists by public key id when / is present" taggedAs Tag("pummelo") in {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T12:36:30.731Z","hwDeviceId":"31a12f36-61a4-4337-827b-ca5655b16c53","pubKey":"ZfhPMdG5HgoaiQdSvkM2xZA71ACdSYqxbsc+VxV6JI0=","pubKeyId":"ZfhPMdG5HgoaiQdSvkM2xZA71ACdSYqxbsc+VxV6JI0=","validNotAfter":"2032-01-31T12:36:30.731Z","validNotBefore":"2022-01-31T12:36:30.731Z"},"signature":"xnZ8EOa6zYQZTB55SiDyClU4ZlddyTfqYRqJhDLyk4QBOXndI4l7g8BNwaZzJzerwf1UpJwyd9VgcHazwdNSAw=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      get("/v1/pubkey/ZfhPMdG5HgoaiQdSvkM2xZA71ACdSYqxbsc+VxV6JI0=") {
        status should equal(200)
        body should equal(dataKey1)
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T12:37:42.737Z","hwDeviceId":"7cf5b4c3-ac93-4c8f-95b3-ff44a02b43d3","pubKey":"qUx7PcBqMGZmZa/0J07bFWHYF9V7HN5ylUyokEHsUzM=","pubKeyId":"ZfhPMdG5HgoaiQdSvkM2xZA71ACdSYqxbsc+VxV6JI0=","validNotAfter":"2032-01-31T12:37:42.737Z","validNotBefore":"2022-01-31T12:37:42.737Z"},"signature":"FnKwerlgxjfh1nzZHoYIL/aV5slcUqxS4Zh6Jk+XlB3t8UdPl3DdtjwIQo2SP9rr5RE04otZpI7x0xqbUVSCDg=="}"""

      post("/v1/pubkey", body = dataKey2) {
        status should equal(200)
        body should equal(dataKey2)
      }

      get("/v1/pubkey/ZfhPMdG5HgoaiQdSvkM2xZA71ACdSYqxbsc+VxV6JI0=") {
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

    "create a second key when signed by the previous key -json-" taggedAs Tag("soursop") in {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T12:43:55.736Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"Ic1ItMPocr/A0Oy/YJ/s32MtC5MW5Fua7nQbQ3Q8N/0=","pubKeyId":"Ic1ItMPocr/A0Oy/YJ/s32MtC5MW5Fua7nQbQ3Q8N/0=","validNotAfter":"2032-01-31T12:43:55.736Z","validNotBefore":"2022-01-31T12:43:55.736Z"},"signature":"I7X3zDDGJaJWNep8rkTiIu9XPo9xDdutdgVUkOmylGk8fz7Z0vYU1gg2Ip414i75Zde7+KTK+TmpEsCl8OyQDg=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T12:43:56.659Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"FbhLNisIxGlDi38UZ3hGwv0mmXtqjExnBHbcEclOz4A=","pubKeyId":"FbhLNisIxGlDi38UZ3hGwv0mmXtqjExnBHbcEclOz4A=","prevPubKeyId":"Ic1ItMPocr/A0Oy/YJ/s32MtC5MW5Fua7nQbQ3Q8N/0=","validNotAfter":"2032-01-31T12:43:56.659Z","validNotBefore":"2022-01-31T12:43:56.659Z"},"signature":"0etd8obF45abmi1Ev5z3VwlhTr8hW0nucl4o2xQGeU6lxP35wEbfmiCdu6IJh6ciECRWgrYNou/Kj3VzamU4CA==","prevSignature":"L7o/I0W+dwR1A4cdERDAfgdZfo0VXStNkunXYzG6hef2Xmrtx1ABNX9zDTLUqCTvuuTjSaBQvpGhODc6MKejBg=="}""".stripMargin

      post("/v1/pubkey", body = dataKey2) {
        status should equal(200)
        body should equal(dataKey2)
      }

      //This key is not connected to any existing key
      val dataKey3 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T12:43:56.676Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"nWtAGYzryz7ZgBAqxISFMN74HHHuDhQXavl6S4TE4Gs=","pubKeyId":"nWtAGYzryz7ZgBAqxISFMN74HHHuDhQXavl6S4TE4Gs=","prevPubKeyId":"GbhLNisIxGlDi38UZ3hGwv0mmXtqjExnBHbcEclOz4A=","validNotAfter":"2032-01-31T12:43:56.676Z","validNotBefore":"2022-01-31T12:43:56.676Z"},"signature":"Hzb/9D5NAZr5e7O+ferYkfjIZpf2I8rAV/FNR3ZOU8I4lzYlFuY25OaQmRHKcn7Y1VKFI+iA2UaAwH1vYKFBCw==","prevSignature":"xipMXdj2gODGLVRfq9TtaEsBWUQNsgFL8bxeZFy2YVq0IugQLvZVUewwsJ4ER5BLKXi0K77Bx+8/x0AcklXgCA=="}""".stripMargin

      post("/v1/pubkey", body = dataKey3) {
        status should equal(400)
        body should equal("""{"version":"1.0","status":"NOK","errorType":"PubkeyError","errorMessage":"Error creating pub key"}""")
      }

      val expectedKeys = """[{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T12:43:56.659Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"FbhLNisIxGlDi38UZ3hGwv0mmXtqjExnBHbcEclOz4A=","pubKeyId":"FbhLNisIxGlDi38UZ3hGwv0mmXtqjExnBHbcEclOz4A=","prevPubKeyId":"Ic1ItMPocr/A0Oy/YJ/s32MtC5MW5Fua7nQbQ3Q8N/0=","validNotAfter":"2032-01-31T12:43:56.659Z","validNotBefore":"2022-01-31T12:43:56.659Z"},"signature":"0etd8obF45abmi1Ev5z3VwlhTr8hW0nucl4o2xQGeU6lxP35wEbfmiCdu6IJh6ciECRWgrYNou/Kj3VzamU4CA==","prevSignature":"L7o/I0W+dwR1A4cdERDAfgdZfo0VXStNkunXYzG6hef2Xmrtx1ABNX9zDTLUqCTvuuTjSaBQvpGhODc6MKejBg=="},{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T12:43:55.736Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"Ic1ItMPocr/A0Oy/YJ/s32MtC5MW5Fua7nQbQ3Q8N/0=","pubKeyId":"Ic1ItMPocr/A0Oy/YJ/s32MtC5MW5Fua7nQbQ3Q8N/0=","validNotAfter":"2032-01-31T12:43:55.736Z","validNotBefore":"2022-01-31T12:43:55.736Z"},"signature":"I7X3zDDGJaJWNep8rkTiIu9XPo9xDdutdgVUkOmylGk8fz7Z0vYU1gg2Ip414i75Zde7+KTK+TmpEsCl8OyQDg=="}]""".stripMargin

      get("/v1/pubkey/current/hardwareId/6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==") {
        status should equal(200)
        body should equal(expectedKeys)
      }

    }

    "second key should not be created if not signed -json-" taggedAs Tag("sapote") in {

      val dataKey1 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T12:45:59.048Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"EDBJW+CunVnyQzdj1/BqdcpZlKScrsVBTWpK9pWAmf4=","pubKeyId":"EDBJW+CunVnyQzdj1/BqdcpZlKScrsVBTWpK9pWAmf4=","validNotAfter":"2032-01-31T12:45:59.048Z","validNotBefore":"2022-01-31T12:45:59.048Z"},"signature":"NZT3KzmdTN41L/w5+mJkeg9soRqSOIjEVWxf7fIpnEINR+6gFb4HfGbZdBy/V8QqSQVd03Om6qduVQV97uqAAQ=="}""".stripMargin

      post("/v1/pubkey", body = dataKey1) {
        status should equal(200)
        body should equal(dataKey1)
      }

      val dataKey2 = """{"pubKeyInfo":{"algorithm":"ed25519-sha-512","created":"2022-01-31T12:46:59.124Z","hwDeviceId":"6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==","pubKey":"o1mWCLFM9NUpxMGgfK/eGZxqK4UNL9MM6DmbD4/rZKk=","pubKeyId":"o1mWCLFM9NUpxMGgfK/eGZxqK4UNL9MM6DmbD4/rZKk=","validNotAfter":"2032-01-31T12:46:59.124Z","validNotBefore":"2022-01-31T12:46:59.124Z"},"signature":"LNMqfC2pU04+Bp2/Vlutc2kMTI53X05Mum2gZld7PZ486ZoOj5Vr7GaTx8uPadxgrpS2DXN9iVHkuIdEWBtrCA=="}"""

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
