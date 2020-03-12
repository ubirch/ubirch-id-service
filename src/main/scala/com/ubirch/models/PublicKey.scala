package com.ubirch.models

import org.joda.time.DateTime

case class PublicKeyInfo(
    pubKey: String,
    pubKeyId: String,
    hwDeviceId: String,
    algorithm: String,
    validNotAfter: Option[DateTime] = None,
    validNotBefore: DateTime = new DateTime(),
    created: DateTime = new DateTime()
)

case class PublicKey(pubKeyInfo: PublicKeyInfo, signature: String)
