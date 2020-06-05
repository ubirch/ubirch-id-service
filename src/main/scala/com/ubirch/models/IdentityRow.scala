package com.ubirch.models

import java.util.Date

import com.ubirch.util.Hasher

case class IdentityRow(
    id: String,
    data_id: String,
    category: String,
    created: Date,
    data: String,
    description: String
)

object IdentityRow {
  def fromIdentity(identity: Identity): IdentityRow = {
    IdentityRow(identity.id, Hasher.hash(identity.data), identity.category, new Date(), identity.data, identity.description)
  }
}

