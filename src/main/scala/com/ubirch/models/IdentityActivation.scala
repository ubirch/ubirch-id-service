package com.ubirch.models

/**
  * Represents an Identity Activation
  *
  * @param identityId Represents a unique identifier
  * @param ownerId Represents the owner of the identity
  * @param dataHash Represents the hash of the data.
  */

case class IdentityActivation(identityId: String, ownerId: String, dataHash: String) {
  def validate: Boolean = identityId.nonEmpty && ownerId.nonEmpty && dataHash.nonEmpty
}

