package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, EntityQuery, Quoted, SnakeCase }

import javax.inject._
import monix.reactive.Observable

/**
  * Represents the queries for the keys_pub_key_id materialized view.
  */
trait PublicKeyRowByPubKeyIdQueries extends TablePointer[PublicKeyRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[PublicKeyRow] = schemaMeta[PublicKeyRow]("keys_by_pub_key_id")

  def byPubKeyIdQ(pubKeyId: String): Quoted[EntityQuery[PublicKeyRow]] = quote {
    query[PublicKeyRow]
      .filter(x => x.pubKeyInfoRow.pubKeyId == lift(pubKeyId))
      .map(x => x)
  }

}

/**
  * Represents the Data Access Object for the PublicKeyRowByPubKeyId Queries
  * @param connectionService Represents the Connection to Cassandra
  */
@Singleton
class PublicKeyRowByPubKeyIdDAO @Inject() (val connectionService: ConnectionService) extends PublicKeyRowByPubKeyIdQueries {
  val db: CassandraStreamContext[SnakeCase] = connectionService.context

  import db._

  def byPubKeyId(pubKeyId: String): Observable[PublicKeyRow] = run(byPubKeyIdQ(pubKeyId))

}
