#!/bin/bash -x

local=$1
remote_host="https://identity.dev.ubirch.com"
host="http://localhost:8081"

if [ "$local" == "-r" ]
then
  host=$remote_host
fi

echo "=> host: $host"

curl -s -X GET -H "content-type: application/json" $host/api/keyService/v1/pubkey/e686b4ba-26b4-4a6d-8b57-f904299d4a5e | jq .
