package com.ubirch.util

import java.util.{ Base64, UUID }

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.client.protocol.DefaultProtocolSigner
import com.ubirch.crypto.GeneratorKeyFactory
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
object ProtocolHelpers extends LazyLogging {

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

  }

  def packRandomPublicKey(curve: String) = {
    val created = DateUtil.nowUTC
    val validNotAfter = Some(created.plusMonths(6))
    val validNotBefore = created

    for {
      protocolEncoder <- Try(MsgPackProtocolEncoder.getEncoder).toEither
      pkData <- getPublicKey(curve, created, validNotAfter, validNotBefore)
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

}
