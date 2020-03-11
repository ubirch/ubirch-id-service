package com.ubirch.models

import org.joda.time.DateTime

case class PublicKeyInfo(
    algorithm: String,
    created: DateTime = new DateTime(),
    hwDeviceId: String,
    previousPubKeyId: Option[String] = None,
    pubKey: String,
    pubKeyId: String,
    validNotAfter: Option[DateTime] = None,
    validNotBefore: DateTime = new DateTime()
)

case class PublicKey(pubKeyInfo: PublicKeyInfo, signature: String)

