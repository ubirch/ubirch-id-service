/* Cassandra migration for keyspace identity_system.
   Version 3 - 2020-03-16T20:34:43.849772+00:00

   Materialized view for hardware search */

use identity_system;

drop MATERIALIZED VIEW IF exists keys_hw_device_id;

CREATE MATERIALIZED VIEW keys_hw_device_id AS
SELECT *
FROM keys
WHERE hw_device_id is not null
    and  pub_key         is not null
    and pub_key_id       is not null
    and hw_device_id     is not null
    and algorithm        is not null
    and valid_not_after  is not null
    and valid_not_before is not null
    and signature        is not null
    and raw              is not null
    and category         is not null
    and created          is not null
PRIMARY KEY (hw_device_id, pub_key);
