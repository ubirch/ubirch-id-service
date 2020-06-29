package com.ubirch.services.key

import java.security.spec.InvalidKeySpecException
import java.util.Base64

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.InvalidPreSignature
import com.ubirch.crypto.GeneratorKeyFactory
import com.ubirch.crypto.utils.Curve
import com.ubirch.models.{ PublicKey, PublicKeyInfo }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.services.pm.ProtocolMessageService
import com.ubirch.util.PublicKeyUtil
import javax.inject._
import org.joda.time.{ DateTime, DateTimeZone }

import scala.util.Try

/**
  * Represents a Public Key Verification tool
  */
trait PubKeyVerificationService {
  def getCurve(algorithm: String): Try[Curve]
  def validateTime(publicKey: PublicKey): Boolean
  def validate(publicKey: PublicKey): Boolean
  def validate(publicKeyN_1: PublicKey, publicKeyN: PublicKey): Boolean
  def validateFromBase64(publicKey: String, signature: String, message: Array[Byte], curve: Curve): Boolean
  def validate(publicKey: Array[Byte], signature: Array[Byte], message: Array[Byte], curve: Curve): Boolean
  def validate(publicKey: Array[Byte], signature: Array[Byte], curve: Curve): Boolean
  def validateFromBase64(publicKey: String, signature: String, curve: Curve): Boolean
  def validate(pubKeyInfo: PublicKeyInfo, pm: ProtocolMessage): Boolean
}

/**
  * Represents a Default Public Key Verification tool
  * @param jsonConverter Represents a json converter convenience
  * @param pmService Represents the Protocol Message Component
  */
@Singleton
class DefaultPubKeyVerificationService @Inject() (jsonConverter: JsonConverterService, pmService: ProtocolMessageService)
  extends PubKeyVerificationService
  with LazyLogging {

  def getCurve(algorithm: String): Try[Curve] = PublicKeyUtil.associateCurve(algorithm)

  def validateTime(publicKey: PublicKey): Boolean = {
    val now = DateTime.now(DateTimeZone.UTC)
    val validNotBefore = new DateTime(publicKey.pubKeyInfo.validNotBefore)
    val validNotAfter = publicKey.pubKeyInfo.validNotAfter.map(x => new DateTime(x))
    validNotBefore.isBefore(now) && validNotAfter.forall(_.isAfter(now))
  }

  def validate(publicKey: PublicKey): Boolean = {
    (for {
      publicKeyInfoString <- jsonConverter.toString(publicKey.pubKeyInfo)
      curve <- getCurve(publicKey.pubKeyInfo.algorithm).toEither
    } yield {
      validateFromBase64(publicKey.pubKeyInfo.pubKey, publicKey.signature, publicKeyInfoString.getBytes, curve)
    }).fold(e => { logger.error(e.getMessage); false }, x => x)
  }

  def validate(publicKeyN_1: PublicKey, publicKeyN: PublicKey): Boolean = {
    (for {
      publicKeyInfoNString <- jsonConverter.toString(publicKeyN.pubKeyInfo)
      curveN_1 <- getCurve(publicKeyN_1.pubKeyInfo.algorithm).toEither
      prevSigForN <- Try(publicKeyN.prevSignature)
        .map(_.filter(_.nonEmpty).getOrElse(throw InvalidPreSignature(publicKeyN.pubKeyInfo.pubKey)))
        .toEither
    } yield {
      validateFromBase64(publicKeyN_1.pubKeyInfo.pubKey, prevSigForN, publicKeyInfoNString.getBytes, curveN_1)
    }).fold(e => { logger.error("exception={} validation_error={}", e.getClass.getCanonicalName, e.getMessage); false }, x => x)
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

  def validate(pubKeyInfo: PublicKeyInfo, pm: ProtocolMessage): Boolean = {
    (for {
      curve <- getCurve(pubKeyInfo.algorithm)
      verifier <- Try(pmService.protocolVerifier(pubKeyInfo.pubKey, curve))
      verification <- Try(verifier.verify(pm.getUUID, pm.getSigned, 0, pm.getSigned.length, pm.getSignature))
    } yield {
      verification
    }).recover {
      case e: Exception =>
        logger.error("Failed to validate -> exception={} message={}", e.getClass.getCanonicalName, e.getMessage)
        false
    }.get
  }

}
