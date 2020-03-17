#!/bin/bash -x
curl -s -X GET -H "content-type: application/json" -d @pubKey.json http://localhost:8081/api/keyService/v1/pubkey/e686b4ba-26b4-4a6d-8b57-f904299d4a5e | jq .
