package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, EntityQuery, Quoted, SnakeCase }

import javax.inject._
import monix.reactive.Observable

/**
  * Represents the queries for the keys_hw_device_id materialized view.
  */
trait PublicKeyRowByOwnerIdQueries extends TablePointer[PublicKeyRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[PublicKeyRow] = schemaMeta[PublicKeyRow]("keys_by_owner_id")

  def byOwnerIdQ(ownerId: String): Quoted[EntityQuery[PublicKeyRow]] = quote {
    query[PublicKeyRow]
      .filter(x => x.pubKeyInfoRow.ownerId == lift(ownerId))
      .map(x => x)
  }

}

/**
  * Represents the Data Access Object for the PublicKeyRowByOwnerId Queries
  * @param connectionService Represents the Connection to Cassandra
  */
@Singleton
class PublicKeyRowByOwnerIdDAO @Inject() (val connectionService: ConnectionService) extends PublicKeyRowByOwnerIdQueries {
  val db: CassandraStreamContext[SnakeCase] = connectionService.context

  import db._

  def byOwnerId(ownerId: String): Observable[PublicKeyRow] = run(byOwnerIdQ(ownerId))

}
