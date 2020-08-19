package com.ubirch.models

import java.util.{ Base64, Date }

import com.ubirch.protocol.codec.UUIDUtil
import com.ubirch.util.DateUtil
import org.joda.time.{ DateTime, DateTimeZone }
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
    prevPubKeyId: Option[String],
    validNotAfter: Option[Date] = None,
    validNotBefore: Date = new Date(),
    revokedAt: Option[Date] = None
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

  def fixValuesFomMsgPack(jv: JValue): JValue = {
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

  def checkPubKeyId(jv: JValue): JValue = {
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
      publicKeyInfoRow.prevPubKeyId,
      publicKeyInfoRow.validNotAfter,
      publicKeyInfoRow.validNotBefore,
      publicKeyInfoRow.revokedAt
    )
  }
}

/**
  * Represents a public key info and its signature. Used for Json Requests.
  * @param pubKeyInfo Represents a Data Transfer Object for the Public Key
  * @param signature Represents the signature of the pubKeyInfo
  */
case class PublicKey(pubKeyInfo: PublicKeyInfo, signature: String, prevSignature: Option[String] = None) {
  def validateTime: Boolean = {
    val now = DateTime.now(DateTimeZone.UTC)
    val validNotBefore = new DateTime(pubKeyInfo.validNotBefore)
    val validNotAfter = pubKeyInfo.validNotAfter.map(x => new DateTime(x))
    validNotBefore.isBefore(now) && validNotAfter.forall(_.isAfter(now))
  }

  def isNotRevoked: Boolean = pubKeyInfo.revokedAt.isEmpty
  def isRevoked: Boolean = !isNotRevoked

}

/**
  * Companion for the PublicKey Container
  */
object PublicKey {

  def fromPublicKeyRow(publicKeyRow: PublicKeyRow): PublicKey = PublicKey(
    PublicKeyInfo.fromPublicKeyInfoRow(publicKeyRow.pubKeyInfoRow),
    publicKeyRow.signature,
    publicKeyRow.prevSignature
  )

  def sort(publicKeys: Seq[PublicKey]): Seq[PublicKey] = {
    publicKeys
      .sortWith((a, b) => a.pubKeyInfo.created.after(b.pubKeyInfo.created))
      .sortWith((a, _) => a.prevSignature.isDefined)
  }

  def filter(publicKeys: Seq[PublicKey])(p: PublicKey => Boolean, p2: (PublicKey => Boolean)*): Seq[PublicKey] = {
    publicKeys.filter { k =>
      (p +: p2).forall(x => x(k))
    }
  }

  def filterNot(publicKeys: Seq[PublicKey])(p: PublicKey => Boolean, p2: (PublicKey => Boolean)*): Seq[PublicKey] = {
    publicKeys.filterNot { k =>
      (p +: p2).forall(x => x(k))
    }
  }

  def sortAndFilterPubKeys(pubKeys: Seq[PublicKey]): (Seq[PublicKey], Seq[PublicKey]) = {

    def valid(pubKeys: Seq[PublicKey]) = {
      sort {
        filter(pubKeys)(
          _.validateTime,
          _.isNotRevoked
        )
      }
    }

    def invalid(pubKeys: Seq[PublicKey]) = {
      sort {
        filterNot(pubKeys)(
          _.validateTime,
          _.isNotRevoked
        )
      }
    }

    (valid(pubKeys), invalid(pubKeys))
  }

}

/**
  * Represents a Deletion Request.
  * @param publicKey Represents the public key.
  * @param signature Represents the signature of the publicKey
  */
case class PublicKeyDelete(publicKey: String, signature: String)

/**
  * Represents a Public Key Revoke Request.
  * @param publicKey Represents the public key.
  * @param signature Represents the signature of the publicKey
  */
case class PublicKeyRevoke(publicKey: String, signature: String)
