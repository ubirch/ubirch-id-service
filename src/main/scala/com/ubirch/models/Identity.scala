package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }
import javax.inject.Inject
import monix.reactive.Observable

import scala.concurrent.ExecutionContext

case class Identity(id: String, category: String, cert: String) {
  def validate = id.nonEmpty && category.nonEmpty && cert.nonEmpty
}

trait IdentityByIdQueries extends TablePointer[Identity] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[Identity] = schemaMeta[Identity]("identities")

  def byIdAndCatQ(id: String, category: String): db.Quoted[db.EntityQuery[Identity]] = quote {
    query[Identity]
      .filter(x => x.id == lift(id))
      .filter(x => x.category == lift(category))
      .map(x => x)
  }

  def insertQ(identity: Identity): db.Quoted[db.Insert[Identity]] = quote {
    query[Identity].insert(lift(identity))
  }

}

class IdentitiesDAO @Inject() (val connectionService: ConnectionService)(implicit val ec: ExecutionContext) extends IdentityByIdQueries {
  val db: CassandraStreamContext[SnakeCase.type] = connectionService.context

  import db._

  def byIdAndCat(id: String, category: String): Observable[Identity] = run(byIdAndCatQ(id, category))
  def insert(identity: Identity): Observable[Unit] = run(insertQ(identity))

}

