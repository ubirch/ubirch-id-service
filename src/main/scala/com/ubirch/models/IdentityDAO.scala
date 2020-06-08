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
  * @param ec Represents the execution context for async processes.
  */
class IdentitiesDAO @Inject() (val connectionService: ConnectionService)(implicit scheduler: Scheduler) extends IdentitiesQueries {
  val db: CassandraStreamContext[SnakeCase.type] = connectionService.context

  import db._

  def insert(identityRow: IdentityRow): Observable[Unit] = run(insertQ(identityRow))

  def insertIfNotExists(identityRow: IdentityRow): Observable[Int] = {
    byOwnerIdAndIdentityId(identityRow.ownerId, identityRow.identityId)
      .count
      .flatMap { x =>
        if (x > 0) Observable(0)
        else run(insertQ(identityRow)).map(_ => 1)
      }

  }

  def selectAll: Observable[IdentityRow] = run(selectAllQ)

  def byOwnerIdAndIdentityId(ownerId: String, identityId: String): Observable[IdentityRow] = run(byOwnerIdAndIdentityIdQ(ownerId, identityId))

}
