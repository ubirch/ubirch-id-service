package com.ubirch

import com.github.nosan.embedded.cassandra.EmbeddedCassandraFactory
import com.github.nosan.embedded.cassandra.api.Cassandra
import com.github.nosan.embedded.cassandra.api.connection.{ CassandraConnection, DefaultCassandraConnectionFactory }
import com.github.nosan.embedded.cassandra.api.cql.CqlScript
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._

/**
  * Tool for embedding cassandra
  */
trait EmbeddedCassandra {

  class CassandraTest extends LazyLogging {

    @volatile var cassandra: Cassandra = _
    @volatile var cassandraConnectionFactory: DefaultCassandraConnectionFactory = _
    @volatile var connection: CassandraConnection = _

    def start(): Unit = {
      val factory: EmbeddedCassandraFactory = new EmbeddedCassandraFactory()
      //factory.getJvmOptions.addAll(List("-Xms1000m", "-Xmx2000m").asJava)
      cassandra = factory.create()
      cassandra.start()
      cassandraConnectionFactory = new DefaultCassandraConnectionFactory
      connection = cassandraConnectionFactory.create(cassandra)

    }

    def stop(): Unit = {
      if (connection != null) try connection.close()
      catch {
        case ex: Throwable =>
          logger.error("CassandraConnection '" + connection + "' is not closed", ex)
      }
      cassandra.stop()
      if (cassandra != null) cassandra.stop()
    }

  }

}

object EmbeddedCassandra {
  def scripts = List(
    CqlScript.ofString("drop keyspace IF EXISTS identity_system;"),
    CqlScript.ofString("CREATE KEYSPACE identity_system WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"),
    CqlScript.ofString("USE identity_system;"),
    CqlScript.ofClasspath("db/migrations/v1_Identity_Column_Family.cql"),
    CqlScript.ofClasspath("db/migrations/v2_Key_Column_Family.cql"),
    CqlScript.ofClasspath("db/migrations/v3_Materialized_view_for_hardware_search.cql"),
    CqlScript.ofClasspath("db/migrations/v4_Materialized_view_for_pub_key_id_search.cql"),
    CqlScript.ofClasspath("db/migrations/v5_Rebuilding_identities_table.cql"),
    CqlScript.ofClasspath("db/migrations/v6_Renaming_column_names.cql"),
    CqlScript.ofClasspath("db/migrations/v7_identity_states.cql"),
    CqlScript.ofClasspath("db/migrations/v8_previous_signature_column.cql"),
    CqlScript.ofClasspath("db/migrations/v9_Modifying_identity_family_column.cql"),
    CqlScript.ofClasspath("db/migrations/v10_Adding_prevPubKeyId_column.cql")

  )
}
