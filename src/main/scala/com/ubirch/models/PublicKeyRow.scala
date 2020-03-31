package com.ubirch.models

import java.util.Date

import io.getquill.Embedded

case class PublicKeyInfoRow(
    algorithm: String,
    created: Date,
    hwDeviceId: String,
    pubKey: String,
    pubKeyId: String,
    validNotAfter: Option[Date],
    validNotBefore: Date
) extends Embedded

object PublicKeyInfoRow {
  def fromPublicKeyInfo(publicKeyInfo: PublicKeyInfo): PublicKeyInfoRow = {
    PublicKeyInfoRow(
      publicKeyInfo.algorithm,
      publicKeyInfo.created,
      publicKeyInfo.hwDeviceId,
      publicKeyInfo.pubKey,
      publicKeyInfo.pubKeyId,
      publicKeyInfo.validNotAfter,
      publicKeyInfo.validNotBefore
    )
  }
}

case class PublicKeyRow(pubKeyInfoRow: PublicKeyInfoRow, category: String, signature: String, raw: String)
object PublicKeyRow {

  val JSON = 'JSON
  val MSG_PACK = 'MSG_PACK

  def fromPublicKey(category: String, publicKey: PublicKey, raw: String): PublicKeyRow = PublicKeyRow(
    PublicKeyInfoRow.fromPublicKeyInfo(publicKey.pubKeyInfo),
    category,
    publicKey.signature,
    raw
  )

  def fromPublicKeyAsJson(publicKey: PublicKey, raw: String): PublicKeyRow = fromPublicKey(JSON.name, publicKey, raw)

  def fromPublicKeyAsMsgPack(publicKey: PublicKey, raw: String): PublicKeyRow = fromPublicKey(MSG_PACK.name, publicKey, raw)

}
