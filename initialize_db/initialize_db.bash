#! /bin/bash

# This scripts uses curl to initialize some local document in the specified database



ADMIN_USER=admin
ADMIN_PASSWD=admin
DB=localhost:5984/steenwerck100km
DOC_NAME=_local/site_info


####### DON'T CHANGE ANYTHING BELOW THAT LINE #######

PATH=$PATH:$( cd $(dirname $0); echo $PWD;)
source update_document.bash

DOC_URL="http://${ADMIN_USER}:${ADMIN_PASSWD}@${DB}/${DOC_NAME}"

usage() {
cat <<EOF
initialize_db.bash <site_id>

where <site_id> is one of:
  - 0 : Salle des sports
  - 1 : Croix du Bac
  - 2 : Crampon
EOF
}

usage_and_exit() {
  usage
  exit $1
}

[ "$#" != 1 ] && usage_and_exit 1

echo "Initialise database $DOC_URL for site $name"

site_info=$(cat <<EOF
"site-id":$1
EOF
)

update_document $DOC_URL "$site_info"

# Workaround to trigger _changes
update_document http://${ADMIN_USER}:${ADMIN_PASSWD}@${DB}/touch_me ""
