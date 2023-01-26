package com.ubirch.models

import java.time.Instant

/**
  * Since version 3.13 (at least I experienced), quill somehow doesn't recognizes the PublicKeyInfoRow embedded case class.
  * It raises the error below.
  * `exception=com.datastax.oss.driver.api.core.servererrors.InvalidQueryException message=Undefined column name pub_key_info_row in table identity_system.keys_by_owner_id`
  * This issue might be related to [[https://github.com/zio/zio-quill/issues/2680]].
  */
///**
//  *  Represents the Row that is inserted in the DB
//  * @param algorithm Represents the algorithm that the key supports
//  * @param created Represents the creation time for the key. This is value is set by the user.
//  * @param ownerId Represents the owner id of the
//  * @param pubKey Represents the public key
//  * @param pubKeyId Represents the public key id. If not provided, it is set as the pubKey.
//  * @param validNotAfter Represents when in the future the key should not be valid anymore.
//  * @param validNotBefore Represents when in the future the key should be valid from.
//  */
//case class PublicKeyInfoRow(
//    algorithm: String,
//    created: Instant,
//    ownerId: String,
//    pubKey: String,
//    pubKeyId: String,
//    prevPubKeyId: Option[String],
//    validNotAfter: Option[Instant],
//    validNotBefore: Instant,
//    revokedAt: Option[Instant]
//) extends Embedded
//
///**
//  * Represents the companion object for the PublicKeyInfoRow
//  */
//object PublicKeyInfoRow {
//  def fromPublicKeyInfo(publicKeyInfo: PublicKeyInfo): PublicKeyInfoRow = {
//    PublicKeyInfoRow(
//      publicKeyInfo.algorithm,
//      publicKeyInfo.created,
//      publicKeyInfo.hwDeviceId,
//      publicKeyInfo.pubKey,
//      publicKeyInfo.pubKeyId,
//      publicKeyInfo.prevPubKeyId,
//      publicKeyInfo.validNotAfter,
//      publicKeyInfo.validNotBefore,
//      publicKeyInfo.revokedAt
//    )
//  }
//}
//
//case class PublicKeyRow(pubKeyInfoRow: PublicKeyInfoRow, category: String, signature: String, prevSignature: Option[String], raw: String)

/**
  * Represents a public key info and its signature and category and raw message that is inserted into the DB
  * @param algorithm Represents the algorithm that the key supports
  * @param created Represents the creation time for the key. This is value is set by the user.
  * @param ownerId Represents the owner id of the
  * @param pubKey Represents the public key
  * @param pubKeyId Represents the public key id. If not provided, it is set as the pubKey.
  * @param validNotAfter Represents when in the future the key should not be valid anymore.
  * @param validNotBefore Represents when in the future the key should be valid from.
  * @param category Represents a category for the key
  * @param signature Represents the signature of the pubKeyInfo
  * @param raw Represents the raw request.
  */
case class PublicKeyRow(
    algorithm: String,
    created: Instant,
    ownerId: String,
    pubKey: String,
    pubKeyId: String,
    prevPubKeyId: Option[String],
    validNotAfter: Option[Instant],
    validNotBefore: Instant,
    revokedAt: Option[Instant],
    category: String,
    signature: String,
    prevSignature: Option[String],
    raw: String
)

/**
  * Represents a companion object for the PublicKeyRow
  */
object PublicKeyRow {

  def fromPublicKey(publicKey: PublicKey, raw: Raw): PublicKeyRow = PublicKeyRow(
    publicKey.pubKeyInfo.algorithm,
    publicKey.pubKeyInfo.created.toInstant,
    publicKey.pubKeyInfo.hwDeviceId,
    publicKey.pubKeyInfo.pubKey,
    publicKey.pubKeyInfo.pubKeyId,
    publicKey.pubKeyInfo.prevPubKeyId,
    publicKey.pubKeyInfo.validNotAfter.map(_.toInstant),
    publicKey.pubKeyInfo.validNotBefore.toInstant,
    publicKey.pubKeyInfo.revokedAt.map(_.toInstant),
    raw.category.toString,
    publicKey.signature,
    publicKey.prevSignature,
    raw.data
  )

}

