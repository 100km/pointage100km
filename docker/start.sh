#! /bin/sh
#

if [ "$1" = replicate ]; then
  shift
  echo Starting couchdb and replicate "$@"
  /entrypoint.sh couchdb &
  cd /home/steenwerck && exec gosu steenwerck pointage100km/bin/replicate "$@"
fi

if [ "$1" = couchdb ]; then
  echo Starting couchdb
  exec /entrypoint.sh couchdb
fi

exec "$@"
