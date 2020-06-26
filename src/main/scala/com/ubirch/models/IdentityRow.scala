package com.ubirch.models

import java.util.Date

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
    dataId: String,
    category: String,
    created: Date,
    data: String,
    description: String
)

/**
  * Represents a convenience object for the Identity Row object.
  */
object IdentityRow {

  def apply(ownerId: String, identityId: String, dataId: String, category: String, data: String, description: String): IdentityRow =
    new IdentityRow(ownerId, identityId, dataId, category, new Date, data, description)

}

