/* Cassandra migration for keyspace identity_system.
   Version 2 - 2020-03-12T091426.527701+0000

   Key Column Family */

drop table if exists keys;

create table if not exists keys(
    pub_key          text,
    pub_key_id       text,
    hw_device_id     text,
    algorithm        text,
    valid_not_after  timestamp,
    valid_not_before timestamp,
    signature        text,
    raw              text,
    created          timestamp,
    PRIMARY KEY (pub_key)
);
