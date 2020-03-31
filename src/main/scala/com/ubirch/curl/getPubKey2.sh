#!/bin/bash -x

local=$1
remote_host="https://identity.dev.ubirch.com"
host="http://localhost:8081"

if [ "$local" == "-r" ]
then
  host=$remote_host
fi

echo "=> host: $host"

harwareId="d947fc53656ddb845435a7b922a5faad1e2067530a656d95f183eb1cca1fcec46137b501a47380a63dfc583268c5e82449519907afc5a917105a36b1242967db"

curl -s -X GET -H "content-type: application/json" $host/api/keyService/v1/pubkey/current/hardwareId/$harwareId | jq .
