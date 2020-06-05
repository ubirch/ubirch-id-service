package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }
import javax.inject.Inject
import monix.reactive.Observable

import scala.concurrent.ExecutionContext

/**
  * Represents the queries for the Identity Column Family.
  */
trait IdentitiesQueries extends TablePointer[IdentityRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[IdentityRow] = schemaMeta[IdentityRow]("identities")

  //keys ((id, data_id), category)
  def byIdAndDataIdQ(id: String, dataId: String): db.Quoted[db.EntityQuery[IdentityRow]] = quote {
    query[IdentityRow]
      .filter(x => x.id == lift(id))
      .filter(x => x.data_id == lift(dataId))
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
class IdentitiesDAO @Inject() (val connectionService: ConnectionService)(implicit val ec: ExecutionContext) extends IdentitiesQueries {
  val db: CassandraStreamContext[SnakeCase.type] = connectionService.context

  import db._

  def insert(identityRow: IdentityRow): Observable[Unit] = run(insertQ(identityRow))

  def selectAll: Observable[IdentityRow] = run(selectAllQ)

  def byIdAndDataId(id: String, dataId: String): Observable[IdentityRow] = run(byIdAndDataIdQ(id, dataId))

}
