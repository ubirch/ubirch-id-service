--This file is a collection of all cql files for easier usage by docker-compose.yml

DROP KEYSPACE if exists identity_system;
CREATE KEYSPACE identity_system WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};

use identity_system;

drop table if exists identities;

create table identities
(
    owner_id text,
    identity_id text,
    data_id text,
    category text,
    created timestamp,
    data text,
    description text,
    primary key ((owner_id), identity_id, data_id, created)
    ) with clustering order by (identity_id desc, data_id desc, created desc);

drop table if exists identities_by_state;

create table identities_by_state
(
    owner_id text,
    identity_id text,
    state text,
    created timestamp,
    primary key (identity_id, state)
);

drop table if exists keys;

create table if not exists keys(
    pub_key          text,
    pub_key_id       text,
    owner_id         text,
    algorithm        text,
    valid_not_after  timestamp,
    valid_not_before timestamp,
    signature         text,
    raw               text,
    category          text,
    prev_signature    text,
    prev_pub_key_id   text,
    "revoked_at"      timestamp,
    created           timestamp,
    PRIMARY KEY (pub_key_id, owner_id)
);

drop MATERIALIZED VIEW IF exists keys_hw_device_id;

CREATE MATERIALIZED VIEW keys_hw_device_id AS
SELECT *
FROM keys
WHERE owner_id is not null
    and pub_key         is not null
    and pub_key_id       is not null
    and algorithm        is not null
    and valid_not_after  is not null
    and valid_not_before is not null
    and signature        is not null
    and raw              is not null
    and category         is not null
    and created          is not null
PRIMARY KEY (owner_id, pub_key_id);

drop MATERIALIZED VIEW IF exists keys_by_pub_key_id;

CREATE MATERIALIZED VIEW keys_by_pub_key_id AS
SELECT *
FROM keys
WHERE pub_key_id is not null
    and pub_key          is not null
    and owner_id         is not null
    and algorithm        is not null
    and valid_not_after  is not null
    and valid_not_before is not null
    and signature        is not null
    and raw              is not null
    and category         is not null
    and created          is not null
PRIMARY KEY (pub_key_id, owner_id);
