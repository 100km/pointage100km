#! /bin/bash

couchapps=$(ls -1 */.couchapprc | xargs -l1 dirname)
for couchapp in $couchapps; do
  cp -rf common/* $couchapp/
  (cd $couchapp && couchapp push)
done
