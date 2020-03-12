package com.ubirch.models

import java.util.Date

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{CassandraStreamContext, SnakeCase}
import javax.inject.Inject
import monix.reactive.Observable
import org.joda.time.DateTime
import io.getquill.Embedded

import scala.concurrent.ExecutionContext

case class PublicKeyInfo(
                          pubKey: String,
                          pubKeyId: String,
                          hwDeviceId: String,
                          algorithm: String,
                          validNotAfter: Option[Date] = None,
                          validNotBefore: Date = new Date(),
                          created: Date = new Date()
                        ) extends Embedded

case class PublicKey(pubKeyInfo: PublicKeyInfo, signature: String)

trait PublicKeyQueries extends TablePointer[PublicKey] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[PublicKey] = schemaMeta[PublicKey]("keys")

  def byPubKeyQ(pubKey: String): db.Quoted[db.EntityQuery[PublicKey]] = quote {
    query[PublicKey]
      .filter(x => x.pubKeyInfo.pubKey == lift(pubKey))
      .map(x => x)
  }

  def insertQ(publicKey: PublicKey): db.Quoted[db.Insert[PublicKey]] = quote {
    query[PublicKey].insert(lift(publicKey))
  }

}

class PublicKeyDAO @Inject()(val connectionService: ConnectionService)(implicit val ec: ExecutionContext) extends PublicKeyQueries {
  val db: CassandraStreamContext[SnakeCase.type] = connectionService.context

  import db._

  def byIdAndCat(pubKey: String): Observable[PublicKey] = run(byPubKeyQ(pubKey))

  def insert(publicKey: PublicKey): Observable[Unit] = run(insertQ(publicKey))

}
