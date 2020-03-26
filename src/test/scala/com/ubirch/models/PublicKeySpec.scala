package com.ubirch.models

import java.util.{Base64, UUID}

import com.ubirch.TestBase
import com.ubirch.crypto.GeneratorKeyFactory
import com.ubirch.services.formats.{JsonConverterService, JsonFormatsProvider}
import com.ubirch.services.key.PubKeyVerificationService
import com.ubirch.services.pm.ProtocolMessageService
import com.ubirch.util.{DateUtil, PublicKeyUtil}
import org.apache.commons.codec.binary.Hex
import org.joda.time.format.ISODateTimeFormat
import org.json4s.Extraction
import org.json4s.JsonAST.{JInt, JString}

import scala.util.Try

class PublicKeySpec extends TestBase {

  private val dateTimeFormat = ISODateTimeFormat.dateTime()

  implicit val formats = new JsonFormatsProvider {} get ()
  val jsonConverter = new JsonConverterService()
  val pmService = new ProtocolMessageService()
  val verification = new PubKeyVerificationService(jsonConverter, pmService)

  "PublicKey" must {
    s"be successfully stringified and verified using ${PublicKeyUtil.ECDSA} and ${PublicKeyUtil.EDDSA}" in {

      def go(curveName: String) {

        val curve = PublicKeyUtil.associateCurve(curveName)
        val newPrivKey = GeneratorKeyFactory.getPrivKey(curve)
        val newPublicKey = Base64.getEncoder.encodeToString(newPrivKey.getRawPublicKey)
        val hardwareDeviceId = UUID.randomUUID()

        val now = DateUtil.nowUTC
        val inSixMonths = now.plusMonths(6)
        val pubKeyUUID = UUID.randomUUID()
        val pubKeyInfo = PublicKeyInfo(
          algorithm = curveName,
          created = now.toDate,
          hwDeviceId = hardwareDeviceId.toString,
          pubKey = newPublicKey,
          pubKeyId = pubKeyUUID.toString,
          validNotAfter = Some(inSixMonths.toDate),
          validNotBefore = now.toDate
        )

        val res = for {
          publicKeyInfoAsString <- jsonConverter.toString[PublicKeyInfo](pubKeyInfo)
          signature <- Try(Base64.getEncoder.encodeToString(newPrivKey.sign(publicKeyInfoAsString.getBytes))).toEither
          publicKey = PublicKey(pubKeyInfo, signature)
          publicKeyAsString <- jsonConverter.toString[PublicKey](publicKey)
          verificationRes <- Try(verification.validate(publicKey)).toEither
          invalidVerification <- Try(verification.validate(publicKey.copy(signature = publicKey.signature.substring(2)))).toEither
        } yield {

          val nowString = dateTimeFormat.print(now)
          val inSixMonthsString = dateTimeFormat.print(inSixMonths)

          val expectedPublicKeyInfo = s"""{"algorithm":"${pubKeyInfo.algorithm}","created":"$nowString","hwDeviceId":"$hardwareDeviceId","pubKey":"${pubKeyInfo.pubKey}","pubKeyId":"${pubKeyInfo.pubKeyId}","validNotAfter":"$inSixMonthsString","validNotBefore":"$nowString"}"""
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


    "parse from device" in {
      val input = """{"hwDeviceId": "UBIR<q\xbf\x88\x1f\xa4<q\xbf\x88\x1f\xa4", "pubKey": "\xe8\xb1X:yY\x11\xbaH!\xf6\xd1YW\xfb\xb2\xa8\xb6X\x9f\xac\x0e(\xf0F\x9f\x1d]FN\x179", "pubKeyId": "\xe8\xb1X:yY\x11\xbaH!\xf6\xd1YW\xfb\xb2\xa8\xb6X\x9f\xac\x0e(\xf0F\x9f\x1d]FN\x179", "algorithm": "ECC_ED25519", "created": 1560520400, "validNotAfter": 1591278800, "validNotBefore": 1560520400}"""
      jsonConverter.toJValue(input) match {
        case Right(value) =>
          val newValue = value.mapField {
            case ("created", JInt(num)) => ("created", JString(DateUtil.ISOFormatter.print(num.toLong * 1000)))
            case ("validNotAfter", JInt(num)) => ("validNotAfter", JString(DateUtil.ISOFormatter.print(num.toLong * 1000)))
            case ("validNotBefore", JInt(num)) => ("validNotBefore", JString(DateUtil.ISOFormatter.print(num.toLong * 1000)))
            case ("pubKey", JString(key)) => ("pubKey", JString(Base64.getEncoder.encodeToString(key.getBytes())))
            case x => x
          }

          println(newValue)

          Extraction.extract[PublicKeyInfo](newValue)
        case Left(value) => fail(value)
      }

    }

  }

}
