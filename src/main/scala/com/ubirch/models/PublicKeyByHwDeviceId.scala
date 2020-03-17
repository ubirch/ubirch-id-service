package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }
import javax.inject._
import monix.reactive.Observable

import scala.concurrent.ExecutionContext

trait PublicKeyByHwDeviceIdQueries extends TablePointer[PublicKey] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[PublicKey] = schemaMeta[PublicKey]("keys_hw_device_id")

  def byHwDeviceIdQ(hwDeviceId: String): db.Quoted[db.EntityQuery[PublicKey]] = quote {
    query[PublicKey]
      .filter(x => x.pubKeyInfo.hwDeviceId == lift(hwDeviceId))
      .map(x => x)
  }

}

@Singleton
class PublicKeyByHwDeviceIdDAO @Inject() (val connectionService: ConnectionService)(implicit val ec: ExecutionContext) extends PublicKeyByHwDeviceIdQueries {
  val db: CassandraStreamContext[SnakeCase.type] = connectionService.context

  import db._

  def byHwDeviceId(hwDeviceId: String): Observable[PublicKey] = run(byHwDeviceIdQ(hwDeviceId))

}
