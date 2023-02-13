package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }

import javax.inject.{ Inject, Singleton }
import monix.reactive.Observable

import java.time.Instant

/**
  * Represents the queries for the key column family.
  */
trait PublicKeyRowQueries extends CassandraBase {

  import db._

  def byPubKeyIdQ(pubKeyId: String) = quote {
    querySchema[PublicKeyRow]("keys")
      .filter(x => x.pubKeyId == lift(pubKeyId))
      .map(x => x)
  }

  def insertQ(publicKeyRow: PublicKeyRow) = quote {
    querySchema[PublicKeyRow]("keys")
      .insertValue(lift(publicKeyRow))
  }

  def revokedAtQ(publicKeyId: String, ownerId: String, revokedAt: Option[Instant]) = quote {
    querySchema[PublicKeyRow]("keys")
      .insert(
        _.pubKeyId -> lift(publicKeyId),
        _.ownerId -> lift(ownerId),
        _.revokedAt -> lift(revokedAt)
      )
  }

  def deleteQ(pubKeyId: String) = quote {
    querySchema[PublicKeyRow]("keys")
      .filter(_.pubKeyId == lift(pubKeyId)).delete
  }

  def getSomeQ(take: Int) = quote {
    querySchema[PublicKeyRow]("keys")
      .take(lift(take))
      .map(x => x)
  }

  def selectAllQ = quote(querySchema[PublicKeyRow]("keys"))

}

/**
  * Represents the Data Access Object for the PublicKey Queries
  * @param connectionService Represents the Connection to Cassandra
  */
@Singleton
class PublicKeyRowDAO @Inject() (val connectionService: ConnectionService) extends PublicKeyRowQueries {
  val db: CassandraStreamContext[SnakeCase] = connectionService.context

  import db._

  def byPubKeyId(pubKeyId: String): Observable[PublicKeyRow] = run(byPubKeyIdQ(pubKeyId))

  def insert(publicKeyRow: PublicKeyRow): Observable[Unit] = run(insertQ(publicKeyRow))

  def revoke(publicKeyId: String, ownerId: String): Observable[Unit] = run(revokedAtQ(publicKeyId, ownerId, Some(Instant.now())))

  def delete(pubKeyId: String): Observable[Unit] = run(deleteQ(pubKeyId))

  def getSome(take: Int): Observable[PublicKeyRow] = run(getSomeQ(take))

  def selectAll: Observable[PublicKeyRow] = run(selectAllQ)

}
