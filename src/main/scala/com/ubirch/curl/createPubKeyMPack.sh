#!/bin/bash -x

local="1"
remote_host="https://identity.dev.ubirch.com"
host="http://localhost:8081"

if [ $local -eq 0 ]
then
  host=$remote_host
fi

curl -X POST --header "Content-Type:application/octet-stream" -d @PublicKeyInPM.mpack $host/api/keyService/v1/pubkey/mpack | jq .

