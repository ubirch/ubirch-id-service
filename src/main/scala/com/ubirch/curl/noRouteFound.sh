#!/bin/bash -x
curl -s -X POST -H "content-type: application/json" -d @pubKey.json http://localhost:8081/api/keyService/v1/pubkeyy | jq .
