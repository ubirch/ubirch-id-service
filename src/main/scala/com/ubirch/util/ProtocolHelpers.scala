package com.ubirch.util

import java.io.{ BufferedWriter, FileWriter }
import java.util.{ Base64, UUID }

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.client.protocol.{ DefaultProtocolSigner, DefaultProtocolVerifier }
import com.ubirch.crypto.GeneratorKeyFactory
import com.ubirch.crypto.utils.Curve
import com.ubirch.models.{ PublicKey, PublicKeyInfo }
import com.ubirch.protocol.codec.{ MsgPackProtocolDecoder, MsgPackProtocolEncoder }
import com.ubirch.protocol.{ ProtocolException, ProtocolMessage }
import com.ubirch.services.formats.{ JsonConverterService, JsonFormatsProvider }
import org.apache.commons.codec.binary.Hex
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods._

import scala.util.{ Failure, Success, Try }

object ProtocolHelpers extends LazyLogging {

  implicit val formats = new JsonFormatsProvider get ()
  val jsonConverter = new JsonConverterService()

  def main(args: Array[String]): Unit = {
    val re = for {
      random <- packRandom(PublicKeyUtil.EDDSA)
      (bytes, _) = random
      res <- unpack[PublicKey](Hex.encodeHexString(bytes)).toEither
      verifier <- Try(protocolVerifier(res.t.pubKeyInfo.pubKey, PublicKeyUtil.associateCurve(res.t.pubKeyInfo.algorithm))).toEither
    } yield {

      val os = new BufferedWriter(new FileWriter("hola.pack"))
      os.write(Hex.encodeHexString(bytes))
      os.close()

      verifier.verify(res.pm.getUUID, res.pm.getSigned, 0, res.pm.getSigned.length, res.pm.getSignature)
    }

    re match {
      case Right(value) =>
        logger.info(value.toString)
      case Left(value) =>
        logger.error(value.getMessage)
    }

  }

  case class UnPacked[T](t: T, pm: ProtocolMessage, rawBody: String)

  def unpack[T: Manifest](bytesAsString: String) = {
    (for {
      bodyString <- Try(bytesAsString)
      _ <- earlyResponseIf(bodyString.isEmpty)(new Exception("Body can't be empty"))
      bodyBytes <- Try(Hex.decodeHex(bodyString))

      decoder = MsgPackProtocolDecoder.getDecoder
      pm <- Try(decoder.decode(bodyBytes))

      payloadJValue <- Try(fromJsonNode(pm.getPayload))
      pt <- Try(payloadJValue.extract[T])
    } yield {
      UnPacked(pt, pm, bodyString)
    }).recover {
      case p: ProtocolException =>
        logger.error(s"Error exception={} message={}", p.getCause.getClass.getCanonicalName, p.getCause.getMessage)
        throw p
    }
  }

  private def earlyResponseIf(condition: Boolean)(response: Exception) =
    if (condition) Failure(response) else Success(())

  def protocolVerifier(pubKeyAsString: String, curve: Curve) = new DefaultProtocolVerifier((_: UUID) => {
    val decoder = Base64.getDecoder
    import decoder._
    val key = GeneratorKeyFactory.getPubKey(decode(pubKeyAsString), curve)
    List(key)
  })

  def packRandom(curve: String) = {
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
