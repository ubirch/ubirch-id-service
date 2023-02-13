package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }

import javax.inject.Inject
import monix.reactive.Observable

/**
  * Represents the queries for the Identity Column Family.
  *
  * @important
  * Since at least quill 3.12, dynamic query might leads to OutOfMemory.
  * Therefore, we need to avoid using it.
  * @see [[https://github.com/zio/zio-quill/issues/2484]]
  */
trait IdentitiesQueries extends CassandraBase {

  import db._

  def byOwnerIdAndIdentityIdAndDataIdQ(ownerId: String, identityId: String, dataId: String) = quote {
    querySchema[IdentityRow]("identities")
      .filter(x => x.ownerId == lift(ownerId))
      .filter(x => x.identityId == lift(identityId))
      .filter(x => x.dataId == lift(dataId))
      .map(x => x)
  }

  def insertQ(identityRow: IdentityRow) = quote {
    querySchema[IdentityRow]("identities").insertValue(lift(identityRow))
  }

  def selectAllQ = quote(querySchema[IdentityRow]("identities"))

}

/**
  * Represents the Data Access Object for the Identity Queries
  * @param connectionService Represents the Connection to Cassandra
  */
class IdentitiesDAO @Inject() (val connectionService: ConnectionService, identityByStateDAO: IdentityByStateDAO) extends IdentitiesQueries {
  val db: CassandraStreamContext[SnakeCase] = connectionService.context

  import db._

  def insertWithState(identityRow: IdentityRow, state: State): Observable[Unit] = {

    for {
      _ <- run(insertQ(identityRow))
      byState = IdentityByStateRow.fromIdentityRow(identityRow, state)
      _ <- identityByStateDAO.insert(byState)
    } yield ()

  }

  def insertWithStateIfNotExists(identityRow: IdentityRow, state: State): Observable[Int] = {
    byOwnerIdAndIdentityIdAndDataId(identityRow.ownerId, identityRow.identityId, identityRow.dataId)
      .count
      .flatMap { x =>
        if (x > 0) Observable(0)
        else insertWithState(identityRow, state).map(_ => 2) // Two because two records are inserted
      }

  }

  def selectAll: Observable[IdentityRow] = run(selectAllQ)

  def byOwnerIdAndIdentityIdAndDataId(ownerId: String, identityId: String, dataId: String): Observable[IdentityRow] = run(byOwnerIdAndIdentityIdAndDataIdQ(ownerId, identityId, dataId))

}
