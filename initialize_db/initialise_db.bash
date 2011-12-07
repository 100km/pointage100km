#! /bin/bash

# This scripts uses curl to initialize some local document in the specified database



ADMIN_USER=admin
ADMIN_PASSWD=admin
DB=localhost:5984/steenwerck100km



####### DON'T CHANGE ANYTHING BELOW THAT LINE #######

FULL_DB="http://${ADMIN_USER}:${ADMIN_PASSWD}@${DB}/_local"

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

which curl &> /dev/null || usage_and_exit 1


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

echo "Initialise database $FULL_DB for site $name"

cat <<EOF > data.txt
{
"_id":"site_info",
"name":"$name"
}
EOF


cat <<EOF
curl -X PUT $FULL_DB
curl -X POST $FULL_DB -H "Content-Type: application/json" --data @data.txt
EOF
curl -X PUT $FULL_DB --data {}
curl -X POST $FULL_DB -H "Content-Type: application/json" --data @data.txt
