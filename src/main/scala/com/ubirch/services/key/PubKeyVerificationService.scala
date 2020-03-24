package com.ubirch.services.key

import java.security.spec.InvalidKeySpecException
import java.util.Base64

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.crypto.GeneratorKeyFactory
import com.ubirch.crypto.utils.{ Curve, Utils }
import com.ubirch.models.PublicKey
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.util.PublicKeyUtil
import javax.inject._
import org.apache.commons.codec.binary.Hex
import org.joda.time.{ DateTime, DateTimeZone }

@Singleton
class PubKeyVerificationService @Inject() (jsonConverter: JsonConverterService) extends LazyLogging {

  def getCurve(algorithm: String): Curve = PublicKeyUtil.associateCurve(algorithm)

  def validateTime(publicKey: PublicKey): Boolean = {
    val now = DateTime.now(DateTimeZone.UTC)
    val validNotBefore = new DateTime(publicKey.pubKeyInfo.validNotBefore)
    val validNotAfter = publicKey.pubKeyInfo.validNotAfter.map(x => new DateTime(x))
    validNotBefore.isBefore(now) && validNotAfter.exists(_.isAfter(now))
  }

  def validate(publicKey: PublicKey): Boolean = {

    publicKey.raw match {

      //TODO: This part is possible to be removed after tests
      case Some(raw) =>
        val sigIndex: Int = raw.charAt(2) match {
          // check v2 of the ubirch-protocol encoding, but make sure we don't fall for a mix (legacy bin encoding)
          case '2' if raw.charAt(4).toLower != 'b' =>
            logger.debug(s"Registration message: '$raw'")
            66
          // legacy ubirch-protocol encoding
          case _ =>
            logger.warn(s"Legacy registration message: '$raw'")
            67
        }
        val part: Array[Byte] = Hex.decodeHex(raw).dropRight(sigIndex)
        logger.debug("pubKey: '%s'".format(publicKey.pubKeyInfo.pubKey))
        logger.debug("signed part: '%s'".format(Hex.encodeHexString(part)))

        val curve = getCurve(publicKey.pubKeyInfo.algorithm)
        val message = Utils.hash(part, PublicKeyUtil.associateHash(publicKey.pubKeyInfo.algorithm))
        validateFromBase64(publicKey.pubKeyInfo.pubKey, publicKey.signature, message, curve)

      case None =>

        jsonConverter.toString(publicKey.pubKeyInfo) match {

          case Right(publicKeyInfoString) =>
            //TODO added prevPubKey signature check!!!
            logger.info(s"publicKeyInfoString: '$publicKeyInfoString'")
            val curve = getCurve(publicKey.pubKeyInfo.algorithm)
            validateFromBase64(publicKey.pubKeyInfo.pubKey, publicKey.signature, publicKeyInfoString.getBytes, curve)
          case Left(e) =>
            logger.error(e.getMessage)
            false

        }

    }
  }

  def validateFromBase64(publicKey: String, signature: String, message: Array[Byte], curve: Curve): Boolean = {
    val decoder = Base64.getDecoder
    import decoder._
    try {
      validate(decode(publicKey), decode(signature), message, curve)
    } catch {
      case e: Exception =>
        logger.error("Failed to decode 1 -> exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
        false
    }
  }

  def validate(publicKey: Array[Byte], signature: Array[Byte], message: Array[Byte], curve: Curve): Boolean = {
    try {
      GeneratorKeyFactory
        .getPubKey(publicKey, curve)
        .verify(message, signature)
    } catch {
      case e: InvalidKeySpecException =>
        logger.error("Failed to decode 2 -> exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
        false
    }
  }

  def validate(publicKey: Array[Byte], signature: Array[Byte], curve: Curve): Boolean = {
    validate(publicKey, signature, publicKey, curve)
  }

  def validateFromBase64(publicKey: String, signature: String, curve: Curve): Boolean = {
    val decoder = Base64.getDecoder
    import decoder._
    try {
      val pubKey = decode(publicKey)
      validate(pubKey, decode(signature), pubKey, curve)
    } catch {
      case e: Exception =>
        logger.error("Failed to decode 3 -> exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
        false
    }
  }

}
