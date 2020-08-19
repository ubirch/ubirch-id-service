package com.ubirch.models

import java.util.Date

import io.getquill.Embedded

/**
  *  Represents the Row that is inserted in the DB
  * @param algorithm Represents the algorithm that the key supports
  * @param created Represents the creation time for the key. This is value is set by the user.
  * @param ownerId Represents the owner id of the
  * @param pubKey Represents the public key
  * @param pubKeyId Represents the public key id. If not provided, it is set as the pubKey.
  * @param validNotAfter Represents when in the future the key should not be valid anymore.
  * @param validNotBefore Represents when in the future the key should be valid from.
  */
case class PublicKeyInfoRow(
    algorithm: String,
    created: Date,
    ownerId: String,
    pubKey: String,
    pubKeyId: String,
    prevPubKeyId: Option[String],
    validNotAfter: Option[Date],
    validNotBefore: Date,
    revokedAt: Option[Date]
) extends Embedded

/**
  * Represents the companion object for the PublicKeyInfoRow
  */
object PublicKeyInfoRow {
  def fromPublicKeyInfo(publicKeyInfo: PublicKeyInfo): PublicKeyInfoRow = {
    PublicKeyInfoRow(
      publicKeyInfo.algorithm,
      publicKeyInfo.created,
      publicKeyInfo.hwDeviceId,
      publicKeyInfo.pubKey,
      publicKeyInfo.pubKeyId,
      publicKeyInfo.prevPubKeyId,
      publicKeyInfo.validNotAfter,
      publicKeyInfo.validNotBefore,
      publicKeyInfo.revokedAt
    )
  }
}

/**
  * Represents a public key info and its signature and category and raw message that is inserted into the DB
  * @param pubKeyInfoRow Represents a Data Transfer Object for the Public Key
  * @param category Represents a category for the key
  * @param signature Represents the signature of the pubKeyInfo
  * @param raw Represents the raw request.
  */
case class PublicKeyRow(pubKeyInfoRow: PublicKeyInfoRow, category: String, signature: String, prevSignature: Option[String], raw: String)

/**
  * Represents a companion object for the PublicKeyRow
  */
object PublicKeyRow {

  def fromPublicKey(publicKey: PublicKey, raw: Raw): PublicKeyRow = PublicKeyRow(
    PublicKeyInfoRow.fromPublicKeyInfo(publicKey.pubKeyInfo),
    raw.category.toString,
    publicKey.signature,
    publicKey.prevSignature,
    raw.data
  )

}

