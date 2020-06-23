package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }
import javax.inject.Inject
import monix.execution.Scheduler
import monix.reactive.Observable

/**
  * Represents the queries for the Identity Column Family.
  */
trait IdentitiesQueries extends TablePointer[IdentityRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[IdentityRow] = schemaMeta[IdentityRow]("identities")

  //keys (owner_id, identity_id)
  def byOwnerIdAndIdentityIdQ(ownerId: String, identityId: String): db.Quoted[db.EntityQuery[IdentityRow]] = quote {
    query[IdentityRow]
      .filter(x => x.ownerId == lift(ownerId))
      .filter(x => x.identityId == lift(identityId))
      .map(x => x)
  }

  def insertQ(identityRow: IdentityRow): db.Quoted[db.Insert[IdentityRow]] = quote {
    query[IdentityRow].insert(lift(identityRow))
  }

  def selectAllQ: db.Quoted[db.EntityQuery[IdentityRow]] = quote(query[IdentityRow])

}

/**
  * Represents the Data Access Object for the Identity Queries
  * @param connectionService Represents the Connection to Cassandra
  * @param scheduler Represents the execution context scheduler.
  */
class IdentitiesDAO @Inject() (val connectionService: ConnectionService, identityByStateDAO: IdentityByStateDAO)(implicit scheduler: Scheduler) extends IdentitiesQueries {
  val db: CassandraStreamContext[SnakeCase.type] = connectionService.context

  import db._

  def insertWithState(identityRow: IdentityRow, state: State): Observable[Unit] = {

    for {
      _ <- run(insertQ(identityRow))
      byState = IdentityByStateRow.fromIdentityRow(identityRow, state)
      _ <- identityByStateDAO.insert(byState)
    } yield ()

  }

  def insertWithStateIfNotExists(identityRow: IdentityRow, state: State): Observable[Int] = {
    byOwnerIdAndIdentityId(identityRow.ownerId, identityRow.identityId)
      .count
      .flatMap { x =>
        if (x > 0) Observable(0)
        else insertWithState(identityRow, state).map(_ => 2) // Two because two records are inserted
      }

  }

  def selectAll: Observable[IdentityRow] = run(selectAllQ)

  def byOwnerIdAndIdentityId(ownerId: String, identityId: String): Observable[IdentityRow] = run(byOwnerIdAndIdentityIdQ(ownerId, identityId))

}
