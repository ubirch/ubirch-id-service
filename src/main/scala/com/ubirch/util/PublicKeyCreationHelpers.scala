package com.ubirch.util

import java.util.{ Base64, UUID }

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.client.protocol.DefaultProtocolSigner
import com.ubirch.crypto.{ GeneratorKeyFactory, PrivKey }
import com.ubirch.models.{ PublicKey, PublicKeyInfo }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.protocol.codec.MsgPackProtocolEncoder
import com.ubirch.services.formats.{ JsonConverterService, JsonFormatsProvider }
import com.ubirch.services.key.PubKeyVerificationService
import com.ubirch.services.pm.ProtocolMessageService
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods._

import scala.util.Try

/**
  * A tool for creating keys for testing
  */
object PublicKeyCreationHelpers extends LazyLogging {

  implicit val formats = new JsonFormatsProvider get ()
  val jsonConverter = new JsonConverterService()
  val pmService = new ProtocolMessageService()
  val verification = new PubKeyVerificationService(jsonConverter, pmService)

  def main(args: Array[String]): Unit = {
    /*    val re = for {
      random <- packRandomPublicKeyInfo(PublicKeyUtil.EDDSA)
      (bytes, _) = random
      res <- pmService.unpackFromBytes(bytes).toEither
      verification <- Try(verification.validate(res.payload, res.pm)).toEither
    } yield {

      val bw = new BufferedWriter(new FileWriter("src/main/scala/com/ubirch/curl/data.mpack"))
      bw.write(Hex.encodeHexString(bytes))
      bw.close()

      val os = new FileOutputStream("src/main/scala/com/ubirch/curl/data2.mpack")
      os.write(bytes)
      os.close()

      verification

    }

    re match {
      case Right(value) =>
        logger.info(value.toString)
      case Left(value) =>
        logger.error(value.getMessage)
    }*/

    println(randomPublicKeyWithPrevSignature)

  }

  def randomPublicKeyWithPrevSignature = {
    for {
      hardwareDeviceId <- Try("6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==").toEither //Try(UUID.randomUUID().toString).toEither
      random1 <- randomPublicKey(PublicKeyUtil.EDDSA, hardwareDeviceId)
      (_, publicKey1AsString, _, _, prevPrivKey) = random1
      random2 <- randomPublicKey(PublicKeyUtil.EDDSA, hardwareDeviceId)
      (pk2, _, _, _, _) = random2
      signed <- sign(pk2.pubKeyInfo, prevPrivKey)
      (_, prevSignature, _) = signed
      publicKey2 = pk2.copy(prevSignature = Option(prevSignature))
      publicKey2AsString <- jsonConverter.toString[PublicKey](publicKey2)
    } yield {
      (publicKey1AsString, publicKey2AsString)
    }
  }

  def randomPublicKey(
      curve: String,
      hardwareDeviceId: String = UUID.randomUUID().toString,
      pubKeyId: Option[String] = None
  ) = {

    val created = DateUtil.nowUTC
    val validNotAfter = Some(created.plusMonths(6))
    val validNotBefore = created

    for {
      pkData <- getPublicKey(curve, created, validNotAfter, validNotBefore, hardwareDeviceId, pubKeyId)
      (pk, _, _, _, _) = pkData
      _ = logger.info(pk.pubKeyInfo.hwDeviceId)
    } yield {
      pkData
    }

  }

  def packRandomPublicKey(
      curve: String,
      hardwareDeviceId: UUID = UUID.randomUUID(),
      pubKeyId: Option[String] = None
  ) = {
    val created = DateUtil.nowUTC
    val validNotAfter = Some(created.plusMonths(6))
    val validNotBefore = created

    for {
      protocolEncoder <- Try(MsgPackProtocolEncoder.getEncoder).toEither
      pkData <- getPublicKey(curve, created, validNotAfter, validNotBefore, hardwareDeviceId.toString, pubKeyId)
      (pk, pkAsString, _, _, privKey) = pkData
      _ = logger.info(pk.pubKeyInfo.hwDeviceId)
      uuid = UUID.fromString(pk.pubKeyInfo.hwDeviceId)
      publicKeyAsJValue <- jsonConverter.toJValue[PublicKey](pk)
      pm = new ProtocolMessage(ProtocolMessage.SIGNED, uuid, 1, asJsonNode(publicKeyAsJValue))
      protocolSigner = new DefaultProtocolSigner(_ => Some(privKey))
      bytes <- Try(protocolEncoder.encode(pm, protocolSigner)).toEither
    } yield {
      (bytes, pkAsString)
    }

  }

  def packRandomPublicKeyInfo(curve: String) = {
    val created = DateUtil.nowUTC
    val validNotAfter = Some(created.plusMonths(6))
    val validNotBefore = created

    for {
      protocolEncoder <- Try(MsgPackProtocolEncoder.getEncoder).toEither
      pkData <- getPublicKey(curve, created, validNotAfter, validNotBefore)
      (pk, pkAsString, _, _, privKey) = pkData
      _ = logger.info(pk.pubKeyInfo.hwDeviceId)
      uuid = UUID.fromString(pk.pubKeyInfo.hwDeviceId)
      publicKeyInfoAsJValue <- jsonConverter.toJValue[PublicKeyInfo](pk.pubKeyInfo)
      pm = new ProtocolMessage(ProtocolMessage.SIGNED, uuid, 1, asJsonNode(publicKeyInfoAsJValue))
      protocolSigner = new DefaultProtocolSigner(_ => Some(privKey))
      bytes <- Try(protocolEncoder.encode(pm, protocolSigner)).toEither
    } yield {
      (bytes, pkAsString)
    }

  }

  def getPublicKey(
      curveName: String,
      created: DateTime,
      validNotAfter: Option[DateTime],
      validNotBefore: DateTime,
      hardwareDeviceId: String = UUID.randomUUID().toString,
      pubKeyId: Option[String] = None
  ) = {

    for {
      curve <- PublicKeyUtil.associateCurve(curveName).toEither
      newPrivKey <- Try(GeneratorKeyFactory.getPrivKey(curve)).toEither
      newPublicKey = Base64.getEncoder.encodeToString(newPrivKey.getRawPublicKey)
      pubKeyInfo = PublicKeyInfo(
        algorithm = curveName,
        created = created.toDate,
        hwDeviceId = hardwareDeviceId,
        pubKey = newPublicKey,
        pubKeyId = pubKeyId.getOrElse(newPublicKey),
        validNotAfter = validNotAfter.map(_.toDate),
        validNotBefore = validNotBefore.toDate
      )
      signed <- sign(pubKeyInfo, newPrivKey)
      (_, signature, signatureAsBytes) = signed
      publicKey = PublicKey(pubKeyInfo, signature)
      publicKeyAsString <- jsonConverter.toString[PublicKey](publicKey)
    } yield {
      (publicKey, publicKeyAsString, signatureAsBytes, signature, newPrivKey)
    }

  }

  def sign(publicKeyInfo: PublicKeyInfo, newPrivKey: PrivKey) = {
    for {
      publicKeyInfoAsString <- jsonConverter.toString[PublicKeyInfo](publicKeyInfo)
      signatureAsBytes <- Try(newPrivKey.sign(publicKeyInfoAsString.getBytes)).toEither
      signature <- Try(Base64.getEncoder.encodeToString(signatureAsBytes)).toEither
    } yield {
      (publicKeyInfoAsString, signature, signatureAsBytes)
    }
  }

}
