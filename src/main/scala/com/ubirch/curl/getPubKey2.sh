#!/bin/bash -x

local=$1
remote_host="https://identity.dev.ubirch.com"
host="http://localhost:8081"

if [ "$local" == "-r" ]
then
  host=$remote_host
fi

echo "=> host: $host"

harwareId="55424952-3c71-bf88-1fa4-3c71bf881fa4"

curl -s -X GET -H "content-type: application/json" $host/api/keyService/v1/pubkey/current/hardwareId/$harwareId | jq .
