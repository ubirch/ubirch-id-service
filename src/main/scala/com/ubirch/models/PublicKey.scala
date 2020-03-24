package com.ubirch.models

import java.util.Date

import io.getquill.Embedded

case class PublicKeyInfo(
    algorithm: String,
    created: Date = new Date(),
    hwDeviceId: String,
    pubKey: String,
    pubKeyId: String,
    validNotAfter: Option[Date] = None,
    validNotBefore: Date = new Date()
) extends Embedded

case class PublicKey(pubKeyInfo: PublicKeyInfo, signature: String, raw: Option[String] = None)

case class PublicKeyDelete(pubKey: String, signature: String)
