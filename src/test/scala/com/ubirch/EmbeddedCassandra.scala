package com.ubirch

import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.github.nosan.embedded.cassandra.local.LocalCassandraFactoryBuilder
import com.github.nosan.embedded.cassandra.test.TestCassandra

/**
  * Tool for embedding cassandra
  */
trait EmbeddedCassandra {

  val factory = new LocalCassandraFactoryBuilder().build

  val cassandra = new TestCassandra(factory)

}

object EmbeddedCassandra {
  def scripts = List(
    CqlScript.statements("drop keyspace IF EXISTS identity_system;"),
    CqlScript.statements("CREATE KEYSPACE identity_system WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"),
    CqlScript.statements("USE identity_system;"),
    CqlScript.statements("drop table if exists keys;"),
    CqlScript.statements(
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
    CqlScript.statements("drop MATERIALIZED VIEW IF exists keys_by_owner_id;"),
    CqlScript.statements(
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
    CqlScript.statements("drop MATERIALIZED VIEW IF exists keys_by_pub_key_id;"),
    CqlScript.statements(
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
    CqlScript.statements("drop table if exists identities;"),
    CqlScript.statements(
      """
        |create table identities
        |(
        |	owner_id text,
        |	identity_id text,
        |	category text,
        |	created timestamp,
        |	data text,
        |	description text,
        |	primary key (owner_id, identity_id)
        |);
        |""".stripMargin
    ),
    CqlScript.statements("drop table if exists identities_by_state;"),
    CqlScript.statements(
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
    CqlScript.statements("alter table keys add prev_signature text;")
  )
}
