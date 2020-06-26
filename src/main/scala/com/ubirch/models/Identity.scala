package com.ubirch.models

/**
  * Represents an Identity
  *
  * @param identityId Represents a unique identifier
  * @param category Represents the category for the identity
  * @param data Represents the raw certificate X.509. or CSR It is usually sent as Hex or Base64
  */
case class Identity(ownerId: String, identityId: String, category: String, data: String, description: String) {
  def validate: Boolean = identityId.nonEmpty && category.nonEmpty && data.nonEmpty
}

