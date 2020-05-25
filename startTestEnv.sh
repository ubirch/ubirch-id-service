#!/bin/bash

docker stop $(docker ps -a -q)
kill -9 $(lsof -t -i:9092)
sleep 5s
docker-compose up

function cleanup() {
  docker stop $(docker ps -a -q)
  kill -9 $(lsof -t -i:9092)
}

trap cleanup EXIT
