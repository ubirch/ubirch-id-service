package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraStreamContext, SnakeCase }
import javax.inject.{ Inject, Singleton }
import monix.reactive.Observable

import scala.concurrent.ExecutionContext

trait PublicKeyQueries extends TablePointer[PublicKey] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[PublicKey] = schemaMeta[PublicKey]("keys")

  def byPubKeyQ(pubKey: String): db.Quoted[db.EntityQuery[PublicKey]] = quote {
    query[PublicKey]
      .filter(x => x.pubKeyInfo.pubKey == lift(pubKey))
      .map(x => x)
  }

  def byHwDeviceIdQ(hwDeviceId: String): db.Quoted[db.EntityQuery[PublicKey]] = quote {
    query[PublicKey]
      .filter(x => x.pubKeyInfo.hwDeviceId == lift(hwDeviceId))
      .map(x => x)
  }

  def insertQ(publicKey: PublicKey): db.Quoted[db.Insert[PublicKey]] = quote {
    query[PublicKey].insert(lift(publicKey))
  }

  def deleteQ(pubKey: String): db.Quoted[db.Delete[PublicKey]] = quote {
    query[PublicKey].filter(_.pubKeyInfo.pubKey == lift(pubKey)).delete
  }

}

@Singleton
class PublicKeyDAO @Inject() (val connectionService: ConnectionService)(implicit val ec: ExecutionContext) extends PublicKeyQueries {
  val db: CassandraStreamContext[SnakeCase.type] = connectionService.context

  import db._

  def byPubKey(pubKey: String): Observable[PublicKey] = run(byPubKeyQ(pubKey))

  def insert(publicKey: PublicKey): Observable[Unit] = run(insertQ(publicKey))

  def byHwDeviceId(hwDeviceId: String): Observable[PublicKey] = run(byHwDeviceIdQ(hwDeviceId))

  def delete(pubKey: String): Observable[Unit] = run(deleteQ(pubKey))

}
