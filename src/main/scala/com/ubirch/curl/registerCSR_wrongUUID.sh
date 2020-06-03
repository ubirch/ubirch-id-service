#!/bin/bash -x

local=$1
remote_host="https://identity.dev.ubirch.com"
host="http://localhost:8081"

if [ "$local" == "-r" ]
then
  host=$remote_host
fi

echo "=> host: $host"

curl -s -X POST --header "Content-Type:application/octet-stream" --data-binary @registerCSR_wrongUUID.der $host/api/certs/v1/csr/register | jq .

