package com.ubirch.models

import java.util.Date

import com.ubirch.util.Hasher

/**
  * Represents the identity data access object
  * @param ownerId the id of the identity
  * @param identityId the id of the data - calculated by getting the hash256 of the data.
  * @param category the category/group for the data
  * @param created the date when the record was created
  * @param data the encoded cert/csr
  * @param description a brief description of the identity
  */
case class IdentityRow(
    ownerId: String,
    identityId: String,
    category: String,
    created: Date,
    data: String,
    description: String
)

/**
  * Represents a convenience object for the Identity Row object.
  */
object IdentityRow {
  def fromIdentity(identity: Identity): IdentityRow = {
    IdentityRow(identity.id, Hasher.hash(identity.data), identity.category, new Date(), identity.data, identity.description)
  }
}

