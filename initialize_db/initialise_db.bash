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

cat <<EOF > data.txt
{
"name":"$name",
"site_id":"$1"
}
EOF


get_request=$( curl -s -X GET $DOC_URL )
if [[ "$get_request" =~ "$DOC_NAME" ]]
then
  rev=$( echo $get_request | sed 's#.*_rev\":\"\([^\"]*\)\",.*#\1#' )
  #echo $rev
  echo "The document is already present with revision $rev. Need to delete it first."
  curl -X DELETE $DOC_URL?rev=$rev
fi
curl -X PUT $DOC_URL -H "Content-Type: application/json" --data @data.txt
