package com.ubirch

/**
  * Object that contains configuration keys
  */
object ConfPaths {

  trait ExecutionContextConfPaths {
    val THREAD_POOL_SIZE = "identitySystem.executionContext.threadPoolSize"
  }

  trait CassandraClusterConfPaths {
    val CONTACT_POINTS = "identitySystem.cassandra.cluster.contactPoints"
    val CONSISTENCY_LEVEL = "identitySystem.cassandra.cluster.consistencyLevel"
    val SERIAL_CONSISTENCY_LEVEL = "identitySystem.cassandra.cluster.serialConsistencyLevel"
    val WITH_SSL = "identitySystem.cassandra.cluster.withSSL"
    val USERNAME = "identitySystem.cassandra.cluster.username"
    val PASSWORD = "identitySystem.cassandra.cluster.password"
    val KEYSPACE = "identitySystem.cassandra.cluster.keyspace"
    val PREPARED_STATEMENT_CACHE_SIZE = "identitySystem.cassandra.cluster.preparedStatementCacheSize"
  }

  trait ConsumerConfPaths {
    val BOOTSTRAP_SERVERS = "identitySystem.kafkaConsumer.bootstrapServers"
    val TOPICS_PATH = "identitySystem.kafkaConsumer.topics"
    val MAX_POLL_RECORDS = "identitySystem.kafkaConsumer.maxPollRecords"
    val GROUP_ID_PATH = "identitySystem.kafkaConsumer.groupId"
    val GRACEFUL_TIMEOUT_PATH = "identitySystem.kafkaConsumer.gracefulTimeout"
    val METRICS_SUB_NAMESPACE = "identitySystem.kafkaConsumer.metricsSubNamespace"
    val FETCH_MAX_BYTES_CONFIG = "identitySystem.kafkaConsumer.fetchMaxBytesConfig"
    val MAX_PARTITION_FETCH_BYTES_CONFIG = "identitySystem.kafkaConsumer.maxPartitionFetchBytesConfig"
    val RECONNECT_BACKOFF_MS_CONFIG = "identitySystem.kafkaConsumer.reconnectBackoffMsConfig"
    val RECONNECT_BACKOFF_MAX_MS_CONFIG = "identitySystem.kafkaConsumer.reconnectBackoffMaxMsConfig"
  }

  trait ProducerConfPaths {
    val LINGER_MS = "identitySystem.kafkaProducer.lingerMS"
    val BOOTSTRAP_SERVERS = "identitySystem.kafkaProducer.bootstrapServers"
    val ERROR_TOPIC_PATH = "identitySystem.kafkaProducer.errorTopic"
    val TOPIC_PATH = "identitySystem.kafkaProducer.topic"
  }

  trait PrometheusConfPaths {
    val PORT = "identitySystem.metrics.prometheus.port"
  }

  object ConsumerConfPaths extends ConsumerConfPaths
  object ProducerConfPaths extends ProducerConfPaths

}
