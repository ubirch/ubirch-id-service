package com.ubirch.util

import java.util.{ Base64, UUID }

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.client.protocol.DefaultProtocolSigner
import com.ubirch.crypto.{ GeneratorKeyFactory, PrivKey }
import com.ubirch.models.{ PublicKey, PublicKeyInfo }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.protocol.codec.MsgPackProtocolEncoder
import com.ubirch.services.formats.{ DefaultJsonConverterService, JsonFormatsProvider }
import com.ubirch.services.key.DefaultPubKeyVerificationService
import com.ubirch.services.pm.DefaultProtocolMessageService
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods._

import scala.util.Try

/**
  * A tool for creating keys for testing
  */
object PublicKeyCreationHelpers extends LazyLogging {

  implicit val formats = new JsonFormatsProvider get ()
  val jsonConverter = new DefaultJsonConverterService()
  val pmService = new DefaultProtocolMessageService()
  val verification = new DefaultPubKeyVerificationService(jsonConverter, pmService)

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

    val r = randomPublicKeyWithPrevSignature
    println(r.map(_._1))
    println(r.map(_._2))
    println(r.map(_._3))

    //    val t = Base64.getDecoder.decode("MC4CAQAwBQYDK2VwBCIEIBYmyz0/gPl/BgrQQQK3br/FvcumTsqTBIIBPr+7DNEo")
    //
    //    val privKey = GeneratorKeyFactory.getPrivKey(t.slice(t.length - 32, t.length), Curve.Ed25519)
    //
    //    val created = DateUtil.nowUTC
    //    val validNotAfter = Some(created.plusMonths(6))
    //    val validNotBefore = created
    //    val hardwareDeviceId: String = "a0d1f73c-8819-4a97-b96b-49cabd3eba47"
    //
    //    println(Base64.getEncoder.encodeToString(privKey.getPublicKey.getEncoded))
    //    println(Base64.getEncoder.encodeToString(privKey.getRawPublicKey))
    //
    //    val (a, b, c, d, e) = PublicKeyCreationHelpers.getPublicKey2(privKey, "ED25519", created, validNotAfter, validNotBefore, hardwareDeviceId = hardwareDeviceId).toTry.get
    //    //val csr = CertUtil.createCSR2(UUID.fromString(hardwareDeviceId))(new KeyPair(e.getPublicKey, e.getPrivateKey), "ED25519")
    //    println(b)
  }

  def randomPublicKeyWithPrevSignature = {
    for {
      //hardwareDeviceId <- Try("6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA==").toEither // Try("7cf5b4c3-ac93-4c8f-95b3-ff44a02b43d3").toEither
      hardwareDeviceId <- Try("c0eee73e-0ee5-40ed-b021-d9717e26330e").toEither

      curve <- Try(PublicKeyUtil.ECDSA).toEither
      ///
      random1 <- randomPublicKey(curve, hardwareDeviceId)
      (publicKey1, publicKey1AsString, _, _, prevPrivKey) = random1

      random2 <- randomPublicKey(curve = curve, hardwareDeviceId = hardwareDeviceId, prevPubKeyId = Some(publicKey1.pubKeyInfo.pubKeyId))
      (pk2, _, _, _, prevPrivKey2) = random2

      signed <- sign(pk2.pubKeyInfo, prevPrivKey)
      (_, prevSignature, _) = signed

      publicKey2 = pk2.copy(prevSignature = Option(prevSignature))
      publicKey2AsString <- jsonConverter.toString[PublicKey](publicKey2)

      random3 <- randomPublicKey(curve = curve, hardwareDeviceId = hardwareDeviceId, prevPubKeyId = Some(publicKey2.pubKeyInfo.pubKeyId))
      (pk3, _, _, _, _) = random3

      signed <- sign(pk3.pubKeyInfo, prevPrivKey2)
      (_, prevSignature, _) = signed

      publicKey3 = pk3.copy(prevSignature = Option(prevSignature))

      publicKey3AsString <- jsonConverter.toString[PublicKey](publicKey3)

    } yield {
      (publicKey1AsString, publicKey2AsString, publicKey3AsString)
    }
  }

  def randomPublicKey(
      curve: String,
      hardwareDeviceId: String = UUID.randomUUID().toString,
      pubKeyId: Option[String] = None,
      prevPubKeyId: Option[String] = None
  ) = {

    val created = DateUtil.nowUTC
    val validNotAfter = Some(created.plusMonths(6))
    val validNotBefore = created

    for {
      pkData <- getPublicKey(curve, created, validNotAfter, validNotBefore, hardwareDeviceId, pubKeyId, prevPubKeyId)
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
      pubKeyId: Option[String] = None,
      prevPubKeyId: Option[String] = None
  ): Either[Throwable, (PublicKey, String, Array[Byte], String, PrivKey)] = {

    for {
      curve <- PublicKeyUtil.associateCurve(curveName).toEither
      newPrivKey <- Try(GeneratorKeyFactory.getPrivKey(curve)).toEither
      newPublicKey = Base64.getEncoder.encodeToString(newPrivKey.getRawPublicKey)
      pubKeyInfo = PublicKeyInfo(algorithm = curveName, created = created.toDate, hwDeviceId = hardwareDeviceId, pubKey = newPublicKey, pubKeyId = pubKeyId.getOrElse(newPublicKey), prevPubKeyId = prevPubKeyId, validNotAfter = validNotAfter.map(_.toDate), validNotBefore = validNotBefore.toDate)
      signed <- sign(pubKeyInfo, newPrivKey)
      (_, signature, signatureAsBytes) = signed
      publicKey = PublicKey(pubKeyInfo, signature)
      publicKeyAsString <- jsonConverter.toString[PublicKey](publicKey)
    } yield {
      (publicKey, publicKeyAsString, signatureAsBytes, signature, newPrivKey)
    }

  }

  def getPublicKey2(
      privKey: PrivKey,
      curveName: String,
      created: DateTime,
      validNotAfter: Option[DateTime],
      validNotBefore: DateTime,
      hardwareDeviceId: String = UUID.randomUUID().toString,
      pubKeyId: Option[String] = None
  ): Either[Throwable, (PublicKey, String, Array[Byte], String, PrivKey)] = {

    for {
      newPrivKey <- Try(privKey).toEither
      newPublicKey = Base64.getEncoder.encodeToString(newPrivKey.getRawPublicKey)
      pubKeyInfo = PublicKeyInfo(algorithm = curveName, created = created.toDate, hwDeviceId = hardwareDeviceId, pubKey = newPublicKey, pubKeyId = pubKeyId.getOrElse(newPublicKey), None, validNotAfter = validNotAfter.map(_.toDate), validNotBefore = validNotBefore.toDate)
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
