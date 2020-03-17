#!/bin/bash -x
curl -s -X DELETE -H "content-type: application/json" -d @PublicKeyDelete.json http://localhost:8081/api/keyService/v1/pubkey | jq .
