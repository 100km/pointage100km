#! /bin/bash

# This scripts uses curl to initialize some local document in the specified database



ADMIN_USER=admin
ADMIN_PASSWD=admin
DB=localhost:5984/steenwerck100km
DOC_NAME=_local/site_info


####### DON'T CHANGE ANYTHING BELOW THAT LINE #######

DOC_URL="http://${ADMIN_USER}:${ADMIN_PASSWD}@${DB}/${DOC_NAME}"

usage() {
cat <<EOF
initialise_db.bash <site_id>

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

which curl &> /dev/null || { echo "This script needs curl. Please install it." ; exit 1 ; }


case $1 in
  0)
    name="La salle des sports"
    ;;
  1)
    name="La Croix du Bac"
    ;;
  2)
    name="Le crampon"
    ;;
  *)
    usage
    exit 1;
    ;;
esac

echo "Initialise database $DOC_URL for site $name"


function update_document () {
  url=$1
  doc_json="$2"

  get_request=$( curl -s -X GET $url )
  if ! [[ "$get_request" =~ "missing" ]]
  then
    rev=$( echo $get_request | sed 's#.*_rev\":\"\([^\"]*\)\".*#\1#' )
    echo "The document is already present with revision $rev. Need to add rev."
data=$(cat <<EOF
{
"_rev":"$rev",
$doc_json
}
EOF
)
  fi

  curl -X PUT $url -H "Content-Type: application/json" --data "$data"
}


site_info=$(cat <<EOF
"name":"$name",
"site_id":"$1"
EOF
)

update_document $DOC_URL "$site_info"

# Workaround to trigger _changes
update_document http://${ADMIN_USER}:${ADMIN_PASSWD}@${DB}/touch_me ""
