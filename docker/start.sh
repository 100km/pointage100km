#! /bin/sh
#

start_replicate() {
  cd /home/steenwerck
  while true; do
     pointage100km/bin/replicate "$@"
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
  echo Starting replicate "$@"
  start_replicate "$@"
fi

exec "$@"
