/* Cassandra migration for keyspace identity_system.
   Version 6 - 2020-06-05T14:06:51.130456+00:00

   Renaming column names */

-- we rename the keys table column
alter table keys rename hw_device_id to owner_id;

-- materialized view for owner id
drop MATERIALIZED VIEW IF exists keys_hw_device_id;
drop MATERIALIZED VIEW IF exists keys_by_owner_id;

CREATE MATERIALIZED VIEW keys_by_owner_id AS
SELECT *
FROM keys
WHERE owner_id         is not null
  and pub_key          is not null
  and pub_key_id       is not null
  and algorithm        is not null
  and valid_not_after  is not null
  and valid_not_before is not null
  and signature        is not null
  and raw              is not null
  and category         is not null
  and created          is not null
PRIMARY KEY (owner_id, pub_key_id);

-- materialized view for pub key id

drop MATERIALIZED VIEW IF exists keys_pub_key_id;
drop MATERIALIZED VIEW IF exists keys_by_pub_key_id;

CREATE MATERIALIZED VIEW keys_by_pub_key_id AS
SELECT *
FROM keys
WHERE pub_key_id is not null
  and pub_key         is not null
  and owner_id     is not null
  and algorithm        is not null
  and valid_not_after  is not null
  and valid_not_before is not null
  and signature        is not null
  and raw              is not null
  and category         is not null
  and created          is not null
PRIMARY KEY (pub_key_id, owner_id);


