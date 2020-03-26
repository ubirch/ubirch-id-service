package com.ubirch.services.pm

import java.util.{Base64, UUID}

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.{ArrayNode, BinaryNode, ContainerNode, ObjectNode, TextNode}
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.client.protocol.DefaultProtocolVerifier
import com.ubirch.crypto.GeneratorKeyFactory
import com.ubirch.crypto.utils.Curve
import com.ubirch.protocol.codec.MsgPackProtocolDecoder
import com.ubirch.protocol.{ProtocolException, ProtocolMessage}
import javax.inject._
import org.apache.commons.codec.binary.Hex
import org.json4s.Formats
import org.json4s.jackson.JsonMethods.fromJsonNode
import scala.collection.JavaConverters._

import scala.util.{Failure, Success, Try}

@Singleton
class ProtocolMessageService @Inject() (implicit formats: Formats) extends LazyLogging {

  import ProtocolMessageService._

  def protocolVerifier(pubKeyAsString: String, curve: Curve) = new DefaultProtocolVerifier((_: UUID) => {
    val decoder = Base64.getDecoder
    import decoder._
    val key = GeneratorKeyFactory.getPubKey(decode(pubKeyAsString), curve)
    List(key)
  })

  def unpackFromString[T: Manifest](bytesAsString: String): Try[UnPacked[T]] = {
    (for {
      bodyString <- Try(bytesAsString)
      _ <- earlyResponseIf(bodyString.isEmpty)(new Exception("Body can't be empty"))
      bodyBytes <- Try(Hex.decodeHex(bodyString))

      decoder = MsgPackProtocolDecoder.getDecoder
      pm <- Try(decoder.decode(bodyBytes))

      payloadJValue <- Try(fromJsonNode(pm.getPayload))
      _ = logger.info("protocol_message_payload {}", payloadJValue.toString)
      pt <- Try(payloadJValue.extract[T])
    } yield {
      UnPacked(pt, pm, bodyString)
    }).recover {
      case p: ProtocolException =>
        logger.error(s"Error 1. exception={} message={}", p.getCause.getClass.getCanonicalName, p.getCause.getMessage)
        throw p
    }
  }

  def replaceBinaryNodesWithTextNodes(tree: ContainerNode[_]): Unit = {
    def inner[K](withKeys: (K => Unit) => Unit, get: K => JsonNode, set: (K, JsonNode) => Unit): Unit = {
      withKeys { key =>
        get(key) match {
          case binary: BinaryNode => set(key, new TextNode(binary.asText()))
          case containerNode: ContainerNode[_] => replaceBinaryNodesWithTextNodes(containerNode)
          case _ => set(key, new TextNode("Holla"))
        }
      }
    }

    tree match {
      case o: ObjectNode => inner[String](o.fieldNames().asScala.foreach, o.get, o.set)
      case a: ArrayNode => inner[Int]((0 until a.size()).foreach, a.get, a.set)
    }

    tree
  }

  def unpackFromBytes[T: Manifest](bytes: Array[Byte]): Try[UnPacked[T]] = {
    (for {

      _ <- earlyResponseIf(bytes.isEmpty)(new Exception("Body can't be empty"))
      bytesAsString <- Try(Hex.encodeHexString(bytes))

      decoder = MsgPackProtocolDecoder.getDecoder
      pm <- Try(decoder.decode(bytes))

      payload <- Try(pm.getPayload)
      _ <- Try(replaceBinaryNodesWithTextNodes(payload.asInstanceOf[ContainerNode[_]]))
      payloadJValue <- Try(fromJsonNode(payload))
      _ = logger.info("protocol_message_payload_json_node {}", pm.getPayload.toString)
      _ = logger.info("protocol_message_payload {}", payloadJValue.toString)
      pt <- Try(payloadJValue.extract[T])
    } yield {
      UnPacked(pt, pm, bytesAsString)
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
  case class UnPacked[T](payload: T, pm: ProtocolMessage, rawProtocolMessage: String)
}
