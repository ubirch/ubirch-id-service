/* Cassandra migration for keyspace identity_system.
   Version 8 - 2020-06-08T17:04:31.015456+00:00

   previous signature column */

alter table keys add prev_signature text;
