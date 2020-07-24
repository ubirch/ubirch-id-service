/* Cassandra migration for keyspace identity_system.
   Version 10 - 2020-07-10T15:19:54.510567+00:00

   Adding prevPubKeyId column */

alter table keys add prev_pub_key_id text;
