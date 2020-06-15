package com.ubirch.models

/**
  * Represents the information that will be sent the Event Log System
  * @param hardwareId
  * @param publicKey
  */
case class PublicKeyToAnchor(hardwareId: String, publicKey: String)
