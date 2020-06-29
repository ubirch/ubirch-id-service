/* Cassandra migration for keyspace identity_system.
   Version 7 - 2020-06-08T13:38:27.320877+00:00

   identity states */

drop table if exists identities_by_state;

create table identities_by_state
(
    owner_id text,
    identity_id text,
    state text,
    created timestamp,
    primary key (identity_id, state)
);
