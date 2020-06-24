/* Cassandra migration for keyspace identity_system.
   Version 9 - 2020-06-23T14:22:11.229440+00:00

   Modifying identity family column */

drop table if exists identities;

create table identities
(
	owner_id text,
	identity_id text,
	data_hash text,
	category text,
	created timestamp,
	data text,
	description text,
	primary key ((owner_id), identity_id, data_hash, created)
) with clustering order by (identity_id desc, data_hash desc, created desc);
