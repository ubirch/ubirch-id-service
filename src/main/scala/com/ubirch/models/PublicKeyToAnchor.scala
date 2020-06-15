package com.ubirch.models

/**
  * Represents the information that will be sent the Event Log System
  * @param id of the entity that created the key
  * @param publicKey public key
  */
case class PublicKeyToAnchor(id: String, publicKey: String)
