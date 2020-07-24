/* Cassandra migration for keyspace identity_system.
   Version 11 - 2020-07-24T08:26:15.330447+00:00

   Adding revoke column */

alter table keys add "revoked_at" timestamp;

