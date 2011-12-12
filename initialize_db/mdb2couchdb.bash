#! /bin/bash
#set -x
#set -e

db="$1"
table="$2"
ADMIN_USER=admin
ADMIN_PASSWD=admin
DB=localhost:5984/steenwerck100km
DELIM=^


####### DON'T CHANGE ANYTHING BELOW THAT LINE #######

DOC_URL="http://${ADMIN_USER}:${ADMIN_PASSWD}@${DB}"

PATH=$PATH:$( cd $(dirname $0); echo $PWD; )
source update_document.bash

usage() {
cat <<EOF
./mdb2couchdb.bash <database.mdb> <table_name>

where
  - <database.mdb> is the MDB database.
  - <table_name> is the name of the table to convert
EOF

}

if ! [ -f "$db" ]
then
  echo "ERROR: cannot find the file $db"
  usage
  exit 1
fi

tables=$( mdb-tables -d " " "$db" )
if ! [[ " $tables " =~ " $table " ]]
then
  echo "ERROR: cannot find $table table !"
  usage
  exit 1
fi

fields=( $( mdb-export -S "$db" "$table" | head -n 1 | sed 's/,/ /g') )
echo "Fields are ${fields[*]}"

mdb-export -S -H -d " " "$db" "$table" | grep --fixed-strings -e $DELIM &> /dev/null
if [ "$?" = 0 ]
then
  echo "ERROR: delimtor $DELIM exist in the data, please choose another one."
  exit 1
fi

mdb-export -S -H -d $DELIM "$db" "$table" | while read line
do
  json=$( for ((i=1; i <= ${#fields[@]}; i++))
  do
    echo -n "\"${fields[$(( $i - 1 ))]}\":$( echo $line | cut -d $DELIM -f $i )"
    if [ "$i" != "${#fields[@]}" ]
    then
      echo ","
    fi
  done )

  post_document $DOC_URL "$json"
done

