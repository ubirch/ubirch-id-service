package com.ubirch.models

import java.util.{ Base64, UUID }
import com.ubirch.TestBase
import com.ubirch.crypto.GeneratorKeyFactory
import com.ubirch.services.formats.{ DefaultJsonConverterService, JsonFormatsProvider }
import com.ubirch.services.key.DefaultPubKeyVerificationService
import com.ubirch.services.pm.DefaultProtocolMessageService
import com.ubirch.util.{ DateUtil, PublicKeyUtil }
import org.joda.time.format.ISODateTimeFormat

import java.time.Instant
import scala.util.Try

/**
  * Tests for the creating and verifying keys
  */
class PublicKeySpec extends TestBase {

  private val dateTimeFormat = ISODateTimeFormat.dateTime()

  implicit val formats = new JsonFormatsProvider {} get ()
  val jsonConverter = new DefaultJsonConverterService()
  val pmService = new DefaultProtocolMessageService()
  val verification = new DefaultPubKeyVerificationService(jsonConverter, pmService)

  "PublicKey" must {
    s"be successfully stringified and verified using ${PublicKeyUtil.ECDSA} and ${PublicKeyUtil.EDDSA}" in {

      def go(curveName: String) {

        val hardwareDeviceId = UUID.randomUUID()

        val now = DateUtil.nowUTC
        val inSixMonth = now.plusMonths(6)
        val pubKeyUUID = UUID.randomUUID()

        val res = for {
          curve <- PublicKeyUtil.associateCurve(curveName).toEither
          newPrivKey <- Try(GeneratorKeyFactory.getPrivKey(curve)).toEither
          newPublicKey = Base64.getEncoder.encodeToString(newPrivKey.getRawPublicKey)
          pubKeyInfo = PublicKeyInfo(algorithm = curveName, created = now.toDate, hwDeviceId = hardwareDeviceId.toString, pubKey = newPublicKey, pubKeyId = pubKeyUUID.toString, None, validNotAfter = Some(inSixMonth.toDate), validNotBefore = now.toDate)

          publicKeyInfoAsString <- jsonConverter.toString[PublicKeyInfo](pubKeyInfo)
          signature <- Try(Base64.getEncoder.encodeToString(newPrivKey.sign(publicKeyInfoAsString.getBytes))).toEither
          publicKey = PublicKey(pubKeyInfo, signature)
          publicKeyAsString <- jsonConverter.toString[PublicKey](publicKey)
          verificationRes <- Try(verification.validate(publicKey)).toEither
          invalidVerification <- Try(verification.validate(publicKey.copy(signature = publicKey.signature.substring(2)))).toEither
        } yield {

          val nowString = dateTimeFormat.print(now)
          val inSixMonthString = dateTimeFormat.print(inSixMonth)

          val expectedPublicKeyInfo = s"""{"algorithm":"${pubKeyInfo.algorithm}","created":"$nowString","hwDeviceId":"$hardwareDeviceId","pubKey":"${pubKeyInfo.pubKey}","pubKeyId":"${pubKeyInfo.pubKeyId}","validNotAfter":"$inSixMonthString","validNotBefore":"$nowString"}"""
          val expectedPublicKey = s"""{"pubKeyInfo":$expectedPublicKeyInfo,"signature":"$signature"}""".stripMargin

          assert(expectedPublicKeyInfo == publicKeyInfoAsString)
          assert(expectedPublicKey == publicKeyAsString)
          assert(verificationRes)
          assert(!invalidVerification)

        }

        res.left.map(e => fail(e))
      }

      go(PublicKeyUtil.EDDSA)

      go(PublicKeyUtil.ECDSA)

    }

  }

}
