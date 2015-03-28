#! /bin/sh
#

start_couchdb() {
  while true; do
    /entrypoint.sh couchdb
    echo CouchDB crashed 2>&1
    sleep 5
  done
}

start_replicate() {
  cd /home/steenwerck
  while true; do
     gosu steenwerck pointage100km/bin/replicate "$@"
     if [ $? = 0 ]; then
       echo Replicate exited with status 0
       # Let couchdb some time to persist the data in any case
       sync
       exit 0
     fi
     echo Replicate crashed 2>&1
     sleep 5
  done
}

if [ "$1" = replicate ]; then
  shift
  echo Starting couchdb and replicate "$@"
  start_couchdb &
  sleep 5
  start_replicate "$@"
fi

if [ "$1" = couchdb ]; then
  echo Starting couchdb
  start_couchdb
fi

exec "$@"
