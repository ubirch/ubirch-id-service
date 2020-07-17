# ubirch-id-service

This service knows about public keys and about certs. It is a service that can help devices or user create crypto-identities. It doesn't control 
private keys. It's a system to administer public keys and certificates. 


# Quick Dev Start-Up

To start a test environment for the id service you need to start a cassandra cluster and deploy the schema from
src/main/resources/db/* as well as deploy a Kafka broker.

For easier setup, just run '''docker-compose up'''. Check if the schemaOverview.cql file contains the current schema.
If you want to see the logs remove for the respective docker container the 'logging' property. If the kafka service 
has problems, try to stop all remaining docker container by running 'docker stop $(docker ps -a -q)' and 
'docker rm $(docker ps -a -q)'.

If the cassandra service has issues, you might need to change the sleep duration of the none-seed-nodes. It is important
that node-1 and node-2 are connected to the seed after the seed has reached a certain state and before the load-schema-node
starts its job.                                                

To start the id-service itself run '''mvn exec:java -Dexec.mainClass=com.ubirch.Service'''.

_Commands_:

* Check if the schemaOverview.cql file contains the current schema.

* Run 

```
    docker-compose up
```

```
    mvn exec:java -Dexec.mainClass=com.ubirch.Service
```


