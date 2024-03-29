package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }

import javax.inject._
import monix.reactive.Observable

/**
  * Represents the queries for the keys_pub_key_id materialized view.
  */
trait PublicKeyRowByPubKeyIdQueries extends CassandraBase {

  import db._

  def byPubKeyIdQ(pubKeyId: String) = quote {
    querySchema[PublicKeyRow]("keys_by_pub_key_id")
      .filter(x => x.pubKeyId == lift(pubKeyId))
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
