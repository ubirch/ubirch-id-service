package com.ubirch.models

/**
  * Represents an Identity Activation
  *
  * @param identityId Represents a unique identifier
  * @param ownerId Represents the owner of the identity
  */

case class IdentityActivation(identityId: String, ownerId: String) {
  def validate: Boolean = identityId.nonEmpty && ownerId.nonEmpty
}

