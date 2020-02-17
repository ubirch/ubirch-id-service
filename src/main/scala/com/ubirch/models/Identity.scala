package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraAsyncContext, SnakeCase }
import javax.inject.Inject

import scala.concurrent.{ ExecutionContext, Future }

class Identity(val id: String, val cert: String)

trait IdentityByIdQueries extends TablePointer[Identity] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[Identity] = schemaMeta[Identity]("identity_by_cat")

  def byIdQ(id: String) = quote {
    query[Identity]
      .filter(x => x.id == lift(id))
      .map(x => x)
  }

}

class EventsByCat @Inject() (val connectionService: ConnectionService)(implicit val ec: ExecutionContext) extends IdentityByIdQueries {
  val db: CassandraAsyncContext[SnakeCase.type] = connectionService.context

  import db._

  def byId(id: String): Future[List[Identity]] = run(byIdQ(id))

}

