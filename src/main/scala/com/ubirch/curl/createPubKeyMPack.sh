#!/bin/bash -x
curl -X POST --header "Content-Type:application/octet-stream" -d @PublicKeyInPM.mpack http://localhost:8081/api/keyService/v1/pubkey/mpack | jq .

