
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

user=${1:-admin}
password=${2:-admin}
url="http://$user:$password@localhost:5984"
test_db="test_db"
echo "Deleting previous test database..."
curl -s -X DELETE "$url/$test_db" -H "Content-Type: application/json" 2>&1 | pad_head 2

echo "Pushing all the couch apps..."
script_dir=$(cd $(dirname "$0"); pwd)
couchapps_dir="$script_dir/../couchapps"
(cd $couchapps_dir && ./server-pushapps local $user $password $test_db) 2>&1 | pad_head 2

echo "Pushing test couchapp. Launch the tests at the following URL"
(cd "$script_dir/test_couchapp" && couchapp push $url/$test_db) 2>&1 | pad_head 2
