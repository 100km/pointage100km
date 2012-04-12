
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
test_db="test_db"
echo "Deleting previous test database..."
curl -s -X DELETE "$url/$test_db" -H "Content-Type: application/json" 2>&1 | pad_head 2

couchapps_dir="../couchapps"
(cd $couchapps_dir && ./server-pushapps local "" "" $test_db) 2>&1 | pad_head 2

echo "Pushing test couchapp. Launch the tests at the following URL"
(cd "./test_couchapp" && couchapp push) 2>&1 | pad_head 2
