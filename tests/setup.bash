
function pad_head() {
  n=$1
  while read l; do
    for ((i=0;i<n;i++)); do
      echo -n ">"
    done
    echo -n " "
    echo $l;
  done
  echo
}

which curl &> /dev/null || { echo "This script needs curl. Please install it." ; exit 1 ; }

url="http://admin:admin@localhost:5984"
test_db="$url/test_db"
echo "Deleting previous test database..."
curl -s -X DELETE "$test_db" -H "Content-Type: application/json" 2>&1 | pad_head 2

couchapps_dir="../couchapps"
needed_couchapps="common bib_input"
for couchapp in $needed_couchapps; do
  echo "Pushing needed couchapp $couchapp"
  (cd $couchapps_dir/$couchapp && couchapp push $test_db) 2>&1 | pad_head 2
done

echo "Pushing test couchapp. Launch the tests at the following URL"
(cd "./test_couchapp" && couchapp push) | 2>&1 pad_head 2
