/* Cassandra migration for keyspace identity_system.
   Version 6 - 2020-06-05T03:14:31.149523+00:00

   Column family by state */

drop table if exists identities_by_state;

create table if not exists identities_by_state (
    identity_id text,
    state text,
    created timestamp,
    PRIMARY KEY (identity_id, state)
);
