package com.ubirch

import java.io.File
import java.nio.file.{ Files, Path, Paths }
import java.util.Date

import com.github.nosan.embedded.cassandra.EmbeddedCassandraFactory
import com.github.nosan.embedded.cassandra.api.Cassandra
import com.github.nosan.embedded.cassandra.api.connection.{ CassandraConnection, DefaultCassandraConnectionFactory }
import com.github.nosan.embedded.cassandra.api.cql.CqlScript
import com.typesafe.scalalogging.LazyLogging

import collection.JavaConverters._
import scala.util.Random

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
      factory.getJvmOptions.addAll(List("-Xms1000m", "-Xmx2000m").asJava)
      factory.setRootAllowed(false)

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
    CqlScript.ofString("drop table if exists keys;"),
    CqlScript.ofString(
      """
        |create table if not exists keys(
        |    pub_key          text,
        |    pub_key_id       text,
        |    owner_id         text,
        |    algorithm        text,
        |    valid_not_after  timestamp,
        |    valid_not_before timestamp,
        |    signature         text,
        |    raw               text,
        |    category          text,
        |    created           timestamp,
        |    PRIMARY KEY (pub_key_id, owner_id)
        |);
        """.stripMargin
    ),
    CqlScript.ofString("drop MATERIALIZED VIEW IF exists keys_by_owner_id;"),
    CqlScript.ofString(
      """
        |CREATE MATERIALIZED VIEW keys_by_owner_id AS
        |SELECT *
        |FROM keys
        |WHERE owner_id         is not null
        |  and pub_key          is not null
        |  and pub_key_id       is not null
        |  and algorithm        is not null
        |  and valid_not_after  is not null
        |  and valid_not_before is not null
        |  and signature        is not null
        |  and raw              is not null
        |  and category         is not null
        |  and created          is not null
        |PRIMARY KEY (owner_id, pub_key_id);
        |""".stripMargin
    ),
    CqlScript.ofString("drop MATERIALIZED VIEW IF exists keys_by_pub_key_id;"),
    CqlScript.ofString(
      """
        |CREATE MATERIALIZED VIEW keys_by_pub_key_id AS
        |SELECT *
        |FROM keys
        |WHERE pub_key_id is not null
        |  and pub_key         is not null
        |  and owner_id     is not null
        |  and algorithm        is not null
        |  and valid_not_after  is not null
        |  and valid_not_before is not null
        |  and signature        is not null
        |  and raw              is not null
        |  and category         is not null
        |  and created          is not null
        |PRIMARY KEY (pub_key_id, owner_id);
        |""".stripMargin
    ),
    CqlScript.ofString("drop table if exists identities;"),
    CqlScript.ofString(
      """
        |create table identities
        |(
        |	owner_id text,
        |	identity_id text,
        |	data_id text,
        |	category text,
        |	created timestamp,
        |	data text,
        |	description text,
        |	primary key ((owner_id), identity_id, data_id, created)
        |) with clustering order by (identity_id desc, data_id desc, created desc);
        |""".stripMargin
    ),
    CqlScript.ofString("drop table if exists identities_by_state;"),
    CqlScript.ofString(
      """
        |create table identities_by_state
        |(
        |    owner_id text,
        |    identity_id text,
        |    state text,
        |    created timestamp,
        |    primary key (identity_id, state)
        |);
        |""".stripMargin
    ),
    CqlScript.ofString("alter table keys add prev_signature text;"),
    CqlScript.ofString("alter table keys add prev_pub_key_id text;")
  )
}
