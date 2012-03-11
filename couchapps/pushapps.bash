#! /bin/bash

dest="default"
[[ "$1" != "" ]] && dest="$1"
couchapp pushapps . $dest
couchapp pushdocs _docs $dest
