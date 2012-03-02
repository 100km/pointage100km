
which curl &> /dev/null || { echo "This script needs curl. Please install it." ; exit 1 ; }

url="http://admin:admin@localhost:5984"
test_db="$url/test_db"
echo "Deleting previous test database..."
curl -s -X DELETE "$test_db" -H "Content-Type: application/json"

couchapps_dir="../couchapps"
needed_couchapps="common bib_input"
for couchapp in $needed_couchapps; do
  echo "Pushing needed couchapp $couchapp"
  (cd $couchapps_dir/$couchapp && couchapp push $test_db)
done

echo "Pushing test couchapp. Launch the tests at the following URL"
(cd "./test_couchapp" && couchapp push)
