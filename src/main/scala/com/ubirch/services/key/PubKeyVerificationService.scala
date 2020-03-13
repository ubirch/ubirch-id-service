package com.ubirch.services.key

import java.security.spec.InvalidKeySpecException
import java.util.Base64

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.crypto.GeneratorKeyFactory
import com.ubirch.crypto.utils.Utils
import com.ubirch.models.PublicKey
import com.ubirch.services.formats.JsonConverterService
import com.ubirch.util.PublicKeyUtil
import javax.inject._
import org.apache.commons.codec.binary.Hex

@Singleton
class PubKeyVerificationService @Inject() (jsonConverter: JsonConverterService) extends LazyLogging {

  def validateSignature(publicKey: PublicKey): Boolean = {

    publicKey.raw match {
      case Some(raw) =>
        val sigIndex: Int = raw.charAt(2) match {
          // check v2 of the ubirch-protocol encoding, but make sure we don't fall for a mix (legacy bin encoding)
          case '2' if raw.charAt(4).toLower != 'b' =>
            logger.debug(s"registration message: '$raw'")
            66
          // legacy ubirch-protocol encoding
          case _ =>
            logger.warn(s"legacy registration message: '$raw'")
            67
        }
        val part: Array[Byte] = Hex.decodeHex(raw).dropRight(sigIndex)
        logger.debug("pubKey: '%s'".format(publicKey.pubKeyInfo.pubKey))
        logger.debug("signed part: '%s'".format(Hex.encodeHexString(part)))
        try {
          val pubKeyBytes = Base64.getDecoder.decode(publicKey.pubKeyInfo.pubKey)
          val pubKey = GeneratorKeyFactory.getPubKey(pubKeyBytes, PublicKeyUtil.associateCurve(publicKey.pubKeyInfo.algorithm))
          pubKey.verify(Utils.hash(part, PublicKeyUtil.associateHash(publicKey.pubKeyInfo.algorithm)), Base64.getDecoder.decode(publicKey.signature))
        } catch {
          case e: InvalidKeySpecException =>
            logger.error("failed to validate signature", e)
            false
        }
      case None =>
        jsonConverter.toString(publicKey.pubKeyInfo) match {

          case Right(publicKeyInfoString) =>
            //TODO added prevPubKey signature check!!!
            logger.info(s"publicKeyInfoString: '$publicKeyInfoString'")
            try {
              val pubKeyBytes = Base64.getDecoder.decode(publicKey.pubKeyInfo.pubKey)
              val pubKey = GeneratorKeyFactory.getPubKey(pubKeyBytes, PublicKeyUtil.associateCurve(publicKey.pubKeyInfo.algorithm))
              pubKey.verify(publicKeyInfoString.getBytes, Base64.getDecoder.decode(publicKey.signature))
            } catch {
              case e: InvalidKeySpecException =>
                logger.error("failed to validate signature", e)
                false
            }

          case Left(e) =>
            logger.error(e.getMessage)
            false

        }
    }
  }

}
