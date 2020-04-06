package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }
import javax.inject.{ Inject, Singleton }
import monix.reactive.Observable

import scala.concurrent.ExecutionContext

/**
  * Represents the queries for the key column family.
  */
trait PublicKeyRowQueries extends TablePointer[PublicKeyRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[PublicKeyRow] = schemaMeta[PublicKeyRow]("keys")

  def byPubKeyIdQ(pubKeyId: String): db.Quoted[db.EntityQuery[PublicKeyRow]] = quote {
    query[PublicKeyRow]
      .filter(x => x.pubKeyInfoRow.pubKeyId == lift(pubKeyId))
      .map(x => x)
  }

  def byHwDeviceIdQ(hwDeviceId: String): db.Quoted[db.EntityQuery[PublicKeyRow]] = quote {
    query[PublicKeyRow]
      .filter(x => x.pubKeyInfoRow.hwDeviceId == lift(hwDeviceId))
      .map(x => x)
  }

  def insertQ(PublicKeyRow: PublicKeyRow): db.Quoted[db.Insert[PublicKeyRow]] = quote {
    query[PublicKeyRow].insert(lift(PublicKeyRow))
  }

  def deleteQ(pubKeyId: String): db.Quoted[db.Delete[PublicKeyRow]] = quote {
    query[PublicKeyRow].filter(_.pubKeyInfoRow.pubKeyId == lift(pubKeyId)).delete
  }

}

/**
  * Represents the Data Access Object for the PublicKey Queries
  * @param connectionService Represents the Connection to Cassandra
  * @param ec Represents the execution context for async processes.
  */
@Singleton
class PublicKeyRowDAO @Inject() (val connectionService: ConnectionService)(implicit val ec: ExecutionContext) extends PublicKeyRowQueries {
  val db: CassandraStreamContext[SnakeCase.type] = connectionService.context

  import db._

  def byPubKeyId(pubKeyId: String): Observable[PublicKeyRow] = run(byPubKeyIdQ(pubKeyId))

  def insert(PublicKeyRow: PublicKeyRow): Observable[Unit] = run(insertQ(PublicKeyRow))

  def byHwDeviceId(hwDeviceId: String): Observable[PublicKeyRow] = run(byHwDeviceIdQ(hwDeviceId))

  def delete(pubKeyId: String): Observable[Unit] = run(deleteQ(pubKeyId))

}
