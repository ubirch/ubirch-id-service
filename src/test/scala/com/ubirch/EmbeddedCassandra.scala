package com.ubirch

import com.github.nosan.embedded.cassandra.local.LocalCassandraFactoryBuilder
import com.github.nosan.embedded.cassandra.test.TestCassandra

/**
  * Tool for embedding cassandra
  */
trait EmbeddedCassandra {

  val factory = new LocalCassandraFactoryBuilder().build

  val cassandra = new TestCassandra(factory)

}
