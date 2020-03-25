#!/bin/bash -x

local=$1
remote_host="https://identity.dev.ubirch.com"
host="http://localhost:8081"

if [ "$local" == "-r" ]
then
  host=$remote_host
fi

echo "=> host: $host"

curl -s -X POST -H "content-type: application/json" -d @PublicKey.json $host/api/keyService/v1/pubkeyy | jq .
