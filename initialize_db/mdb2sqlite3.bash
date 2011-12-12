#! /bin/bash
#set -x
#set -e

bn=$( basename "$@" | sed -e 's/\..*//' )
dn=$( dirname "$@")
name="$dn/$bn"
echo "$name"

mdb-schema -S "$@" | perl -wpe 's%^DROP TABLE %DROP TABLE IF EXISTS %i;
s%(\s*\w+\s+)(Memo/Hyperlink|DateTime( \(Short\))?)%$1TEXT%i;
s%(\s*\w+\s+)(Boolean|Byte|Byte|Numeric|Replication ID|(\w+ )?Integer)%$1INTEGER%i;
s%(\s*\w+\s+)(BINARY|OLE|Unknown ([0-9a-fx]+)?)%$1BLOB%i;
s%\s*\(\d+\)\s*(,?[ \t]*)$%${1}%;' \
	| sed -e 's/\<table\>/table_table/' \
	| sed -e 's/\<table\>/table_table/' \
	| sed -e 's/\b\([0-9]\+\)/annee_\1/' \
       	> "$name.sql.txt"

cat "$name.sql.txt" | sqlite3 "$name.db"

mdb-tables -S "$@" | grep -v "MSys" | while read i ; do 
	echo "BEGIN TRANSACTION;";
	mdb-export -S -I "$@" "$i" | sed -e 's/$/;/' \
		| sed -e 's/\<table\>/table_table/' \
		| sed -e 's/ \([0-9]\{4\}\)/annee_\1/g' \
		| sed -e 's/Table des erreurs/Table_des_erreurs/g';
	echo "END TRANSACTION;"  ;
done > "$name.data.txt"

cat "$name.data.txt" | sqlite3 "$name.db"

