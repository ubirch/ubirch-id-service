--This file is a collection of all cql files for easier usage by docker-compose.yml

DROP KEYSPACE if exists identity_system;
CREATE KEYSPACE identity_system WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};

use identity_system;

drop table if exists identities;

create table if not exists identities (
    id text,
    category text,
    cert text,
    PRIMARY KEY (id, category)
);

drop table if exists keys;

create table if not exists keys(
    pub_key          text,
    pub_key_id       text,
    hw_device_id     text,
    algorithm        text,
    valid_not_after  timestamp,
    valid_not_before timestamp,
    signature         text,
    raw               text,
    category          text,
    created           timestamp,
    PRIMARY KEY (pub_key_id, hw_device_id)
);

drop MATERIALIZED VIEW IF exists keys_hw_device_id;

CREATE MATERIALIZED VIEW keys_hw_device_id AS
SELECT *
FROM keys
WHERE hw_device_id is not null
    and pub_key         is not null
    and pub_key_id       is not null
    and algorithm        is not null
    and valid_not_after  is not null
    and valid_not_before is not null
    and signature        is not null
    and raw              is not null
    and category         is not null
    and created          is not null
PRIMARY KEY (hw_device_id, pub_key_id);

drop MATERIALIZED VIEW IF exists keys_pub_key_id;

CREATE MATERIALIZED VIEW keys_pub_key_id AS
SELECT *
FROM keys
WHERE pub_key_id is not null
    and pub_key         is not null
    and hw_device_id     is not null
    and algorithm        is not null
    and valid_not_after  is not null
    and valid_not_before is not null
    and signature        is not null
    and raw              is not null
    and category         is not null
    and created          is not null
PRIMARY KEY (pub_key_id, hw_device_id);