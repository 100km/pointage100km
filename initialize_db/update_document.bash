#! /bin/bash


which curl &> /dev/null || { echo "This script needs curl. Please install it." ; exit 1 ; }


function update_document () {
  url=$1
  doc_json="$2"

  get_request=$( curl -s -X GET $url )
  if ! [[ "$get_request" =~ "missing" ]]
  then
    rev=$( echo $get_request | sed 's#.*_rev\":\"\([^\"]*\)\".*#\1#' )
    echo "The document is already present with revision $rev. Need to add rev."
if [ -z "$doc_json" ]; then
  comma=""
else
  comma=","
fi
data=$(cat <<EOF
{
"_rev":"$rev"$comma
$doc_json
}
EOF
)
  else
data=$(cat <<EOF
{
$doc_json
}
EOF
)
  fi
echo $data > /tmp/docjson

  curl -X PUT $url -H "Content-Type: application/json" --data "$data"
}

function post_document () {
  url=$1
  doc_json="$2"

  data=$(cat <<EOF
{
$doc_json
}
EOF
)

  curl -X POST $url -H "Content-Type: application/json" --data "$data"
}
