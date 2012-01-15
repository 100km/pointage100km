
which curl &> /dev/null || { echo "This script needs curl. Please install it." ; exit 1 ; }

tested_couchapp="bib_input"

rm -rf "./$tested_couchapp"
cp -r "../$tested_couchapp" .
cp -r "overwrite/$tested_couchapp" .


url="http://admin:admin@localhost:5984"
curl -s -X DELETE "$url/test_db/" -H "Content-Type: application/json" 

cd "./$tested_couchapp" && couchapp push


