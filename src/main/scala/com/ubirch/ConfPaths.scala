package com.ubirch

/**
  * Object that contains configuration keys
  */
object ConfPaths {

  trait ExecutionContextConfPaths {
    val THREAD_POOL_SIZE = "id.executionContext.threadPoolSize"
  }

  trait StoreConfPaths {
    val STORE_LOOKUPS = "id.storeLookups"
  }

  trait CassandraClusterConfPaths {
    val CONTACT_POINTS = "id.cassandra.cluster.contactPoints"
    val CONSISTENCY_LEVEL = "id.cassandra.cluster.consistencyLevel"
    val SERIAL_CONSISTENCY_LEVEL = "id.cassandra.cluster.serialConsistencyLevel"
    val WITH_SSL = "id.cassandra.cluster.withSSL"
    val USERNAME = "id.cassandra.cluster.username"
    val PASSWORD = "id.cassandra.cluster.password"
    val KEYSPACE = "id.cassandra.cluster.keyspace"
    val PREPARED_STATEMENT_CACHE_SIZE = "id.cassandra.cluster.preparedStatementCacheSize"
  }

  trait ConsumerConfPaths {
    val BOOTSTRAP_SERVERS = "id.kafkaConsumer.bootstrapServers"
    val TOPIC_PATH = "id.kafkaConsumer.topic"
    val MAX_POLL_RECORDS = "id.kafkaConsumer.maxPollRecords"
    val GROUP_ID_PATH = "id.kafkaConsumer.groupId"
    val GRACEFUL_TIMEOUT_PATH = "id.kafkaConsumer.gracefulTimeout"
    val METRICS_SUB_NAMESPACE = "id.kafkaConsumer.metricsSubNamespace"
    val FETCH_MAX_BYTES_CONFIG = "id.kafkaConsumer.fetchMaxBytesConfig"
    val MAX_PARTITION_FETCH_BYTES_CONFIG = "id.kafkaConsumer.maxPartitionFetchBytesConfig"
    val RECONNECT_BACKOFF_MS_CONFIG = "id.kafkaConsumer.reconnectBackoffMsConfig"
    val RECONNECT_BACKOFF_MAX_MS_CONFIG = "id.kafkaConsumer.reconnectBackoffMaxMsConfig"
  }

  trait ProducerConfPaths {
    val LINGER_MS = "id.kafkaProducer.lingerMS"
    val BOOTSTRAP_SERVERS = "id.kafkaProducer.bootstrapServers"
    val ERROR_TOPIC_PATH = "id.kafkaProducer.errorTopic"
    val TOPIC_PATH = "id.kafkaProducer.topic"
  }

  trait PrometheusConfPaths {
    val PORT = "id.metrics.prometheus.port"
  }

  trait CryptoConfPaths {
    val SERVICE_PK = "crypto.keys.ed25519.signingPrivateKey"
  }

  object StoreConfPaths extends StoreConfPaths
  object ConsumerConfPaths extends ConsumerConfPaths
  object ProducerConfPaths extends ProducerConfPaths

}
