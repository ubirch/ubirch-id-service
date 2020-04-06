package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }
import javax.inject._
import monix.reactive.Observable

import scala.concurrent.ExecutionContext

/**
  * Represents the queries for the keys_hw_device_id materialized view.
  */
trait PublicKeyRowByHwDeviceIdQueries extends TablePointer[PublicKeyRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[PublicKeyRow] = schemaMeta[PublicKeyRow]("keys_hw_device_id")

  def byHwDeviceIdQ(hwDeviceId: String): db.Quoted[db.EntityQuery[PublicKeyRow]] = quote {
    query[PublicKeyRow]
      .filter(x => x.pubKeyInfoRow.hwDeviceId == lift(hwDeviceId))
      .map(x => x)
  }

}

/**
  * Represents the Data Access Object for the PublicKeyRowByHwDeviceId Queries
  * @param connectionService Represents the Connection to Cassandra
  * @param ec Represents the execution context for async processes.
  */
@Singleton
class PublicKeyRowByHwDeviceIdDAO @Inject() (val connectionService: ConnectionService)(implicit val ec: ExecutionContext) extends PublicKeyRowByHwDeviceIdQueries {
  val db: CassandraStreamContext[SnakeCase.type] = connectionService.context

  import db._

  def byHwDeviceId(hwDeviceId: String): Observable[PublicKeyRow] = run(byHwDeviceIdQ(hwDeviceId))

}
