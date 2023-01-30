package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, EntityQuery, Insert, Quoted, SnakeCase }

import javax.inject.Inject
import monix.reactive.Observable

/**
  * Represents the queries for the Identity By State Column Family.
  */
trait IdentitiesByStateQueries extends TablePointer[IdentityByStateRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[IdentityByStateRow] = schemaMeta[IdentityByStateRow]("identities_by_state")

  def insertQ(identityByStateRow: IdentityByStateRow): Quoted[Insert[IdentityByStateRow]] = quote {
    query[IdentityByStateRow].insertValue(lift(identityByStateRow))
  }

  def selectAllQ: Quoted[EntityQuery[IdentityByStateRow]] = quote(query[IdentityByStateRow])

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
