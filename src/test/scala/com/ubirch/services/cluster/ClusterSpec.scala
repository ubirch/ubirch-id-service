package com.ubirch.services.cluster

import com.github.nosan.embedded.cassandra.api.cql.CqlScript
import com.google.inject.Guice
import com.ubirch.{ Binder, EmbeddedCassandra, TestBase }

/**
  * Test for the cassandra cluster
  */
class ClusterSpec extends TestBase with EmbeddedCassandra {

  val cassandra = new CassandraTest

  lazy val serviceInjector = Guice.createInjector(new Binder())

  "Cluster and Cassandra Context" must {

    "be able to get proper instance and do query" in {

      val connectionService = serviceInjector.getInstance(classOf[ConnectionService])

      val db = connectionService.context

      val t = db.executeQuery("SELECT * FROM identities").headOptionL.runToFuture
      assert(await(t).nonEmpty)
    }

    "be able to get proper instance and do query without recreating it" in {

      val connectionService = serviceInjector.getInstance(classOf[ConnectionService])

      val db = connectionService.context

      val t = db.executeQuery("SELECT * FROM Identities").headOptionL.runToFuture
      assert(await(t).nonEmpty)
    }

  }

  override protected def afterAll(): Unit = {

    val connectionService = serviceInjector.getInstance(classOf[ConnectionService])

    val db = connectionService.context

    db.close()

    cassandra.stop()
  }

  override protected def beforeAll(): Unit = {
    cassandra.start()
    List(
      CqlScript.ofString("CREATE KEYSPACE IF NOT EXISTS identity_system  WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };"),
      CqlScript.ofString("drop table if exists identity_system.identities;"),
      CqlScript.ofString("USE identity_system;"),
      CqlScript.ofString(
        """
          |create table if not exists identities (
          |    id text,
          |    category text,
          |    cert text,
          |    PRIMARY KEY (id, category)
          |);
        """.stripMargin
      ),
      CqlScript.ofString(
        "insert into identities (id, category, cert) values ('522f3e64-6ee5-470c-8b66-9edb0cfbf3b1', 'MYID', 'This is a cert');".stripMargin
      )
    ).foreach(x => x.forEachStatement(cassandra.connection.execute _))
  }
}
