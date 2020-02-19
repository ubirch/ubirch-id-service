/* Cassandra migration for keyspace identity_system.
   Version 1 - 2020-02-17T15:39:05.872027+00:00

   Identity Column Family */


drop table if exists identities;

create table if not exists identities (
    id text,
    category text,
    cert text,
    PRIMARY KEY (id, category)
);
