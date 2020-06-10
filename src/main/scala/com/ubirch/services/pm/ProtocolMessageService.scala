package com.ubirch.services.pm

import java.util.{ Base64, UUID }

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.client.protocol.DefaultProtocolVerifier
import com.ubirch.crypto.GeneratorKeyFactory
import com.ubirch.crypto.utils.Curve
import com.ubirch.protocol.codec.KeyMsgPackProtocolDecoder
import com.ubirch.protocol.{ ProtocolException, ProtocolMessage, ProtocolVerifier }
import com.ubirch.services.pm.ProtocolMessageService.UnPacked
import javax.inject._
import org.apache.commons.codec.binary.Hex
import org.json4s.Formats

import scala.util.{ Failure, Success, Try }

trait ProtocolMessageService {
  def protocolVerifier(pubKeyAsString: String, curve: Curve): ProtocolVerifier
  def unpackFromBytes(bytes: Array[Byte]): Try[UnPacked]
}

/**
  * Represents a component for managing Protocol Messages
  * @param formats Represents the json formats for the system
  */
@Singleton
class DefaultProtocolMessageService @Inject() (implicit formats: Formats) extends ProtocolMessageService with LazyLogging {

  import ProtocolMessageService._

  def protocolVerifier(pubKeyAsString: String, curve: Curve): ProtocolVerifier =
    new DefaultProtocolVerifier((_: UUID) => {
      val decoder = Base64.getDecoder
      import decoder._
      val key = GeneratorKeyFactory.getPubKey(decode(pubKeyAsString), curve)
      List(key)
    })

  def unpackFromBytes(bytes: Array[Byte]): Try[UnPacked] = {
    (for {

      _ <- earlyResponseIf(bytes.isEmpty)(new Exception("Body can't be empty"))
      bytesAsString <- Try(Hex.encodeHexString(bytes))
      _ = logger.info("body_as_hex={}", bytesAsString)

      decoder = KeyMsgPackProtocolDecoder.getDecoder
      pm <- Try(decoder.decode(bytes))

    } yield {
      UnPacked(pm, bytesAsString)
    }).recover {
      case p: ProtocolException =>
        logger.error(s"Error 2. exception={} message={}", p.getCause.getClass.getCanonicalName, p.getCause.getMessage)
        throw p
    }
  }

  private def earlyResponseIf(condition: Boolean)(response: Exception) =
    if (condition) Failure(response) else Success(())

}

object ProtocolMessageService {
  case class UnPacked(pm: ProtocolMessage, rawProtocolMessage: String)
}
