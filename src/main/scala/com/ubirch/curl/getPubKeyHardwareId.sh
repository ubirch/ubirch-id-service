#!/bin/bash -x

local=$1
remote_host="https://identity.dev.ubirch.com"
host="http://localhost:8081"

if [ "$local" == "-r" ]
then
  host=$remote_host
fi

echo "=> host: $host"

hardwareId="6waiGQ3EII8Zz6k65b8RTe+dqFfEroR1+T/WIj3io876d82OK05CSxur7qvpBdYtin/LOf9bK78Y8UuLHubYQA=="
hardwareId="55424952-3c71-bf88-1fa4-3c71bf881fa4"

curl -s -X GET -H "content-type: application/json" $host/api/keyService/v1/pubkey/current/hardwareId/$hardwareId | jq .
