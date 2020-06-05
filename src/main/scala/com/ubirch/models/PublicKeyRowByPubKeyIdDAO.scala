package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }
import javax.inject._
import monix.reactive.Observable

import scala.concurrent.ExecutionContext

/**
  * Represents the queries for the keys_pub_key_id materialized view.
  */
trait PublicKeyRowByPubKeyIdQueries extends TablePointer[PublicKeyRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[PublicKeyRow] = schemaMeta[PublicKeyRow]("keys_by_pub_key_id")

  def byPubKeyIdQ(pubKeyId: String): db.Quoted[db.EntityQuery[PublicKeyRow]] = quote {
    query[PublicKeyRow]
      .filter(x => x.pubKeyInfoRow.pubKeyId == lift(pubKeyId))
      .map(x => x)
  }

}

/**
  * Represents the Data Access Object for the PublicKeyRowByPubKeyId Queries
  * @param connectionService Represents the Connection to Cassandra
  * @param ec Represents the execution context for async processes.
  */
@Singleton
class PublicKeyRowByPubKeyIdDAO @Inject() (val connectionService: ConnectionService)(implicit val ec: ExecutionContext) extends PublicKeyRowByPubKeyIdQueries {
  val db: CassandraStreamContext[SnakeCase.type] = connectionService.context

  import db._

  def byPubKeyId(pubKeyId: String): Observable[PublicKeyRow] = run(byPubKeyIdQ(pubKeyId))

}
