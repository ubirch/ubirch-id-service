package com.ubirch.models

import java.util.Date

/**
  * Represents the identity data access object
  * @param ownerId the id of the identity
  * @param identityId the id of the data - calculated by getting the hash256 of the data.
  * @param created the date when the record was created
  *
  */
case class IdentityByStateRow(
    ownerId: String,
    identityId: String,
    state: String,
    created: Date
)

object IdentityByStateRow {
  def fromIdentityRow(identityRow: IdentityRow, state: State): IdentityByStateRow = {
    IdentityByStateRow(identityRow.ownerId, identityRow.identityId, state.toString, identityRow.created)
  }
}

sealed trait State

case object CSRCreated extends State
case object X509Created extends State
case object X509KeyActivated extends State

