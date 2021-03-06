package com.ubirch.models

import java.util.Date

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }
import javax.inject.{ Inject, Singleton }
import monix.reactive.Observable

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

  def insertQ(publicKeyRow: PublicKeyRow): db.Quoted[db.Insert[PublicKeyRow]] = quote {
    query[PublicKeyRow].insert(lift(publicKeyRow))
  }

  def revokedAtQ(publicKeyId: String, ownerId: String, revokedAt: Option[Date]): db.Quoted[db.Insert[PublicKeyRow]] = quote {
    query[PublicKeyRow]
      .insert(
        _.pubKeyInfoRow.pubKeyId -> lift(publicKeyId),
        _.pubKeyInfoRow.ownerId -> lift(ownerId),
        _.pubKeyInfoRow.revokedAt -> lift(revokedAt)
      )
  }

  def deleteQ(pubKeyId: String): db.Quoted[db.Delete[PublicKeyRow]] = quote {
    query[PublicKeyRow].filter(_.pubKeyInfoRow.pubKeyId == lift(pubKeyId)).delete
  }

  def getSomeQ(take: Int): db.Quoted[db.Query[PublicKeyRow]] = quote {
    query[PublicKeyRow]
      .take(lift(take))
      .map(x => x)
  }

  def selectAllQ: db.Quoted[db.EntityQuery[PublicKeyRow]] = quote(query[PublicKeyRow])

}

/**
  * Represents the Data Access Object for the PublicKey Queries
  * @param connectionService Represents the Connection to Cassandra
  */
@Singleton
class PublicKeyRowDAO @Inject() (val connectionService: ConnectionService) extends PublicKeyRowQueries {
  val db: CassandraStreamContext[SnakeCase.type] = connectionService.context

  import db._

  def byPubKeyId(pubKeyId: String): Observable[PublicKeyRow] = run(byPubKeyIdQ(pubKeyId))

  def insert(publicKeyRow: PublicKeyRow): Observable[Unit] = run(insertQ(publicKeyRow))

  def revoke(publicKeyId: String, ownerId: String): Observable[Unit] = run(revokedAtQ(publicKeyId, ownerId, Some(new Date())))

  def delete(pubKeyId: String): Observable[Unit] = run(deleteQ(pubKeyId))

  def getSome(take: Int): Observable[PublicKeyRow] = run(getSomeQ(take))

  def selectAll: Observable[PublicKeyRow] = run(selectAllQ)

}
