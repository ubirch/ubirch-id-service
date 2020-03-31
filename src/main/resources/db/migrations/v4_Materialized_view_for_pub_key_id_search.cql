/* Cassandra migration for keyspace identity_system.
   Version 4 - 2020-03-31T10:46:37.142973+00:00

   Materialized view for pub_key_id search */

use identity_system;

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
