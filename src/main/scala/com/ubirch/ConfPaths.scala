package com.ubirch

/**
  * Object that contains configuration keys
  */
object ConfPaths {

  trait GenericConfPaths {
    final val NAME = "identitySystem.name"
  }

  trait HttpServerConfPaths {
    final val PORT = "identitySystem.server.port"
    final val SWAGGER_PATH = "identitySystem.server.swaggerPath"
  }

  trait ExecutionContextConfPaths {
    final val THREAD_POOL_SIZE = "identitySystem.executionContext.threadPoolSize"
  }

  trait CassandraClusterConfPaths {
    final val CONTACT_POINTS = "identitySystem.cassandra.cluster.contactPoints"
    final val CONSISTENCY_LEVEL = "identitySystem.cassandra.cluster.consistencyLevel"
    final val SERIAL_CONSISTENCY_LEVEL = "identitySystem.cassandra.cluster.serialConsistencyLevel"
    final val WITH_SSL = "identitySystem.cassandra.cluster.withSSL"
    final val USERNAME = "identitySystem.cassandra.cluster.username"
    final val PASSWORD = "identitySystem.cassandra.cluster.password"
    final val KEYSPACE = "identitySystem.cassandra.cluster.keyspace"
    final val PREPARED_STATEMENT_CACHE_SIZE = "identitySystem.cassandra.cluster.preparedStatementCacheSize"
  }

  trait ConsumerConfPaths {
    final val BOOTSTRAP_SERVERS = "identitySystem.kafkaConsumer.bootstrapServers"
    final val TOPICS_PATH = "identitySystem.kafkaConsumer.topics"
    final val MAX_POLL_RECORDS = "identitySystem.kafkaConsumer.maxPollRecords"
    final val GROUP_ID_PATH = "identitySystem.kafkaConsumer.groupId"
    final val GRACEFUL_TIMEOUT_PATH = "identitySystem.kafkaConsumer.gracefulTimeout"
    final val METRICS_SUB_NAMESPACE = "identitySystem.kafkaConsumer.metricsSubNamespace"
    final val FETCH_MAX_BYTES_CONFIG = "identitySystem.kafkaConsumer.fetchMaxBytesConfig"
    final val MAX_PARTITION_FETCH_BYTES_CONFIG = "identitySystem.kafkaConsumer.maxPartitionFetchBytesConfig"
    final val RECONNECT_BACKOFF_MS_CONFIG = "identitySystem.kafkaConsumer.reconnectBackoffMsConfig"
    final val RECONNECT_BACKOFF_MAX_MS_CONFIG = "identitySystem.kafkaConsumer.reconnectBackoffMaxMsConfig"
  }

  trait ProducerConfPaths {
    final val LINGER_MS = "identitySystem.kafkaProducer.lingerMS"
    final val BOOTSTRAP_SERVERS = "identitySystem.kafkaProducer.bootstrapServers"
    final val ERROR_TOPIC_PATH = "identitySystem.kafkaProducer.errorTopic"
    final val TOPIC_PATH = "identitySystem.kafkaProducer.topic"
  }

  trait PrometheusConfPaths {
    final val PORT = "identitySystem.metrics.prometheus.port"
  }

  object GenericConfPaths extends GenericConfPaths
  object HttpServerConfPaths extends HttpServerConfPaths
  object ConsumerConfPaths extends ConsumerConfPaths
  object ProducerConfPaths extends ProducerConfPaths

}
