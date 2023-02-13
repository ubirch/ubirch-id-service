package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }

import javax.inject.Inject
import monix.reactive.Observable

/**
  * Represents the queries for the Identity By State Column Family.
  */
trait IdentitiesByStateQueries extends CassandraBase {

  import db._

  def insertQ(identityByStateRow: IdentityByStateRow) = quote {
    querySchema[IdentityByStateRow]("identities_by_state").insertValue(lift(identityByStateRow))
  }

  def selectAllQ = quote(querySchema[IdentityByStateRow]("identities_by_state"))

}

/**
  * Represents the Data Access Object for the Identity By State Queries
  * @param connectionService Represents the Connection to Cassandra
  */
class IdentityByStateDAO @Inject() (val connectionService: ConnectionService) extends IdentitiesByStateQueries {
  val db: CassandraStreamContext[SnakeCase] = connectionService.context

  import db._

  def insert(identityByStateRow: IdentityByStateRow): Observable[Unit] = run(insertQ(identityByStateRow))

  def selectAll: Observable[IdentityByStateRow] = run(selectAllQ)

}
