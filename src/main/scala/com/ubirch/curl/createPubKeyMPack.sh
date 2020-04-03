#!/bin/bash -x

local=$1
remote_host="https://identity.dev.ubirch.com"
host="http://localhost:8081"

if [ "$local" == "-r" ]
then
  host=$remote_host
fi

echo "=> host: $host"

curl -s -X POST -H "Content-Type:application/octet-stream" --data-binary @PublicKeyInPM.mpack $host/api/keyService/v1/pubkey/mpack | jq .

