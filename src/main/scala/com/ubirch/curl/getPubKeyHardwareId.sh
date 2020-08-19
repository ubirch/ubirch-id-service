#!/bin/bash -x

local=$1
remote_host="https://identity.dev.ubirch.com"
host="http://localhost:8081"

if [ "$local" == "-r" ]
then
  host=$remote_host
fi

echo "=> host: $host"

hardwareId="7cf5b4c3-ac93-4c8f-95b3-ff44a02b43d3"
hardwareId="6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA=="
hardwareId="89806bfd-9b77-42d2-b5c8-864c9c4f4bbd"

curl -s -X GET -H "content-type: application/json" $host/api/keyService/v1/pubkey/current/hardwareId/$hardwareId | jq .
