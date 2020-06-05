package com.ubirch.models

/**
  * Represents an Identity
  *
  * @param id Represents a unique identifier
  * @param category Represents the category for the identity
  * @param data Represents the raw certificate X.509. or CSR It is usually sent as Hex or Base64
  */
case class Identity(
    id: String,
    category: String,
    data: String,
    description: String
) {
  def validate: Boolean = id.nonEmpty && category.nonEmpty && data.nonEmpty
}

