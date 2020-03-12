/* Cassandra migration for keyspace identity_system.
   Version 2 - 2020-03-12T091426.527701+0000

   Key Column Family */

drop table if exists keys;

create table if not exists keys (pubKey text,
                                 pubKeyId text,
                                 hwDeviceId text,
                                 algorithm text,
                                 validNotAfter timestamp,
                                 validNotBefore timestamp,
                                 signature text,
                                 created timestamp,
                                 PRIMARY KEY (pubKey)
                                 );
