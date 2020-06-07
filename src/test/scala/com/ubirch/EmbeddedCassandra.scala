package com.ubirch

import com.github.nosan.embedded.cassandra.EmbeddedCassandraFactory
import com.github.nosan.embedded.cassandra.api.Cassandra
import com.github.nosan.embedded.cassandra.api.connection.{ CassandraConnection, DefaultCassandraConnectionFactory }

/**
  * Tool for embedding cassandra
  */
trait EmbeddedCassandra {
  //https://nosan.github.io/embedded-cassandra/
  val factory: EmbeddedCassandraFactory = new EmbeddedCassandraFactory()
  val cassandra: Cassandra = factory.create()
  lazy val cassandraConnectionFactory: DefaultCassandraConnectionFactory = new DefaultCassandraConnectionFactory
  lazy val connection: CassandraConnection = cassandraConnectionFactory.create(cassandra)

}
