#! /bin/bash

couchapps=$(ls -1 */.couchapprc | xargs -L1 dirname)
for couchapp in $couchapps; do
  cp -rf common/* $couchapp/
  (cd $couchapp && couchapp push)
done
