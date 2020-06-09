package com.ubirch.models

import java.util.{ Base64, Date }

import com.ubirch.protocol.codec.UUIDUtil
import com.ubirch.util.DateUtil
import org.json4s.JsonAST.{ JInt, JObject, JString, JValue }

/**
  * Represents a Data Transfer Object for the Public Key
  * @param algorithm Represents the algorithm that the key supports
  * @param created Represents the creation time for the key. This is value is set by the user.
  * @param hwDeviceId Represents the owner id of the identity
  * @param pubKey Represents the public key
  * @param pubKeyId Represents the public key id. If not provided, it is set as the pubKey.
  * @param validNotAfter Represents when in the future the key should not be valid anymore.
  * @param validNotBefore Represents when in the future the key should be valid from.
  */
case class PublicKeyInfo(
    algorithm: String,
    created: Date,
    hwDeviceId: String,
    pubKey: String,
    pubKeyId: String,
    validNotAfter: Option[Date] = None,
    validNotBefore: Date = new Date()
)

/**
  * Companion object for PublicKeyInfo
  */
object PublicKeyInfo {

  final val ALGORITHM = "algorithm"
  final val HW_DEVICE_ID = "hwDeviceId"
  final val CREATED = "created"
  final val PUB_KEY = "pubKey"
  final val PUB_KEY_ID = "pubKeyId"
  final val VALID_NOT_AFTER = "validNotAfter"
  final val VALID_NOT_BEFORE = "validNotBefore"

  def fixValuesFomMsgPack(jv: JValue) = {
    def formatter(time: Long) = DateUtil.ISOFormatter.print(time.toLong * 1000)
    jv.mapField {
      case (x @ PublicKeyInfo.ALGORITHM, JString(value)) => (x, JString(new String(Base64.getDecoder.decode(value))))
      case (x @ PublicKeyInfo.HW_DEVICE_ID, JString(value)) => (x, JString(UUIDUtil.bytesToUUID(Base64.getDecoder.decode(value)).toString))
      case (x @ PublicKeyInfo.CREATED, JInt(num)) => (x, JString(formatter(num.toLong)))
      case (x @ PublicKeyInfo.VALID_NOT_AFTER, JInt(num)) => (x, JString(formatter(num.toLong)))
      case (x @ PublicKeyInfo.VALID_NOT_BEFORE, JInt(num)) => (x, JString(formatter(num.toLong)))
      case x => x
    }
  }

  def checkPubKeyId(jv: JValue) = {
    val pk = jv.findField {
      case (PublicKeyInfo.PUB_KEY, _) => true
      case _ => false
    }

    val pkId = jv.findField {
      case (PublicKeyInfo.PUB_KEY_ID, _) => true
      case _ => false
    }

    if (pkId.isEmpty && pk.isDefined) {
      val fields = (PublicKeyInfo.PUB_KEY_ID, pk.get._2) +: jv.foldField(List.empty[(String, JValue)])((a, b) => b +: a)
      JObject(fields)
    } else {
      jv
    }
  }

  def fromPublicKeyInfoRow(publicKeyInfoRow: PublicKeyInfoRow): PublicKeyInfo = {
    PublicKeyInfo(
      publicKeyInfoRow.algorithm,
      publicKeyInfoRow.created,
      publicKeyInfoRow.ownerId,
      publicKeyInfoRow.pubKey,
      publicKeyInfoRow.pubKeyId,
      publicKeyInfoRow.validNotAfter,
      publicKeyInfoRow.validNotBefore
    )
  }
}

/**
  * Represents a public key info and its signature. Used for Json Requests.
  * @param pubKeyInfo Represents a Data Transfer Object for the Public Key
  * @param signature Represents the signature of the pubKeyInfo
  */
case class PublicKey(pubKeyInfo: PublicKeyInfo, signature: String, prevSignature: Option[String] = None)

/**
  * Companion for the PublicKey Container
  */
object PublicKey {
  def fromPublicKeyRow(publicKeyRow: PublicKeyRow): PublicKey = PublicKey(
    PublicKeyInfo.fromPublicKeyInfoRow(publicKeyRow.pubKeyInfoRow),
    publicKeyRow.signature,
    publicKeyRow.prevSignature
  )
}

/**
  * Represents a Deletion Requests.
  * @param publicKey Represents the public key.
  * @param signature Represents the signature of the publicKey
  */
case class PublicKeyDelete(publicKey: String, signature: String)
