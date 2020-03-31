#!/bin/bash -x

local=$1
remote_host="https://identity.dev.ubirch.com"
host="http://localhost:8081"

if [ "$local" == "-r" ]
then
  host=$remote_host
fi

echo "=> host: $host"

pubKeyId="VHcuJqKi4JceU1sCoO61cUgUd5blReD/1U1ga4T0JQQ="

curl -s -X GET -H "content-type: application/json" $host/api/keyService/v1/pubkey/$pubKeyId | jq .
