package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }

import javax.inject._
import monix.reactive.Observable

/**
  * Represents the queries for the keys_hw_device_id materialized view.
  */
trait PublicKeyRowByOwnerIdQueries extends CassandraBase {

  import db._

  def byOwnerIdQ(ownerId: String) = quote {
    querySchema[PublicKeyRow]("keys_by_owner_id")
      .filter(x => x.ownerId == lift(ownerId))
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
