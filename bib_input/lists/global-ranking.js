function(head, req) {

  start({
    "headers": {
      "Content-Type": "text/plain"
    }
  });

  var res = {};
  var arr = [];
  var set = {};
  while (row = getRow()) {
    var tmp = row["value"]["bib"];
    if (!set[tmp]) {
      set[tmp] = true;
      arr.push(row);
    }
  }
  res.rows = arr;
  res.total_rows = arr.length;
  return JSON.stringify(res);
}
