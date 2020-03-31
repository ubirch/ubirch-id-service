package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }
import javax.inject.{ Inject, Singleton }
import monix.reactive.Observable

import scala.concurrent.ExecutionContext

trait PublicKeyRowQueries extends TablePointer[PublicKeyRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[PublicKeyRow] = schemaMeta[PublicKeyRow]("keys")

  def byPubKeyQ(pubKey: String): db.Quoted[db.EntityQuery[PublicKeyRow]] = quote {
    query[PublicKeyRow]
      .filter(x => x.pubKeyInfoRow.pubKey == lift(pubKey))
      .map(x => x)
  }

  def byHwDeviceIdQ(hwDeviceId: String): db.Quoted[db.EntityQuery[PublicKeyRow]] = quote {
    query[PublicKeyRow]
      .filter(x => x.pubKeyInfoRow.hwDeviceId == lift(hwDeviceId))
      .map(x => x)
  }

  def insertQ(PublicKeyRow: PublicKeyRow): db.Quoted[db.Insert[PublicKeyRow]] = quote {
    query[PublicKeyRow].insert(lift(PublicKeyRow))
  }

  def deleteQ(pubKey: String): db.Quoted[db.Delete[PublicKeyRow]] = quote {
    query[PublicKeyRow].filter(_.pubKeyInfoRow.pubKey == lift(pubKey)).delete
  }

}

@Singleton
class PublicKeyRowDAO @Inject() (val connectionService: ConnectionService)(implicit val ec: ExecutionContext) extends PublicKeyRowQueries {
  val db: CassandraStreamContext[SnakeCase.type] = connectionService.context

  import db._

  def byPubKey(pubKey: String): Observable[PublicKeyRow] = run(byPubKeyQ(pubKey))

  def insert(PublicKeyRow: PublicKeyRow): Observable[Unit] = run(insertQ(PublicKeyRow))

  def byHwDeviceId(hwDeviceId: String): Observable[PublicKeyRow] = run(byHwDeviceIdQ(hwDeviceId))

  def delete(pubKey: String): Observable[Unit] = run(deleteQ(pubKey))

}
