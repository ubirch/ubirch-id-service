/* Cassandra migration for keyspace identity_system.
   Version 5 - 2020-06-05T03:14:17.315588+00:00

   Rebuilding identities table */

drop table if exists identities;

create table identities
(
	owner_id text,
	identity_id text,
	category text,
	created timestamp,
	data text,
	description text,
	primary key (owner_id, identity_id)
);

