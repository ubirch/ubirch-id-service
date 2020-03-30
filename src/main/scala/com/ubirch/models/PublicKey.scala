package com.ubirch.models

import java.util.Date

case class PublicKeyInfo(
    algorithm: String,
    created: Date,
    hwDeviceId: String,
    pubKey: String,
    pubKeyId: String,
    validNotAfter: Option[Date] = None,
    validNotBefore: Date = new Date()
)

object PublicKeyInfo {
  def fromPublicKeyInfoRow(publicKeyInfoRow: PublicKeyInfoRow): PublicKeyInfo = {
    PublicKeyInfo(
      publicKeyInfoRow.algorithm,
      publicKeyInfoRow.created,
      publicKeyInfoRow.hwDeviceId,
      publicKeyInfoRow.pubKey,
      publicKeyInfoRow.pubKeyId,
      publicKeyInfoRow.validNotAfter,
      publicKeyInfoRow.validNotBefore
    )
  }
}

case class PublicKey(pubKeyInfo: PublicKeyInfo, signature: String)

object PublicKey {
  def fromPublicKeyRow(publicKeyRow: PublicKeyRow): PublicKey = PublicKey(
    PublicKeyInfo.fromPublicKeyInfoRow(publicKeyRow.pubKeyInfoRow),
    publicKeyRow.signature
  )
}

case class PublicKeyDelete(pubKey: String, signature: String)
