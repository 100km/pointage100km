function(head, req) {
  var searched_bib = req.query["bib"];
  var n = req.query["n"] || 0;

  start({
    "headers": {
      "Content-Type": "text/plain"
    }
  });

  var row;
  var bibs = [];
  var rank = 1;
  var res = {};
  if (searched_bib == undefined) {
        res.bibs = [];
  }
  else {
    while (row = getRow()) {
      var tmp = row["value"]["bib"];
      bibs.push(tmp);
      if (tmp == searched_bib) {
        found = true;
        break;
      }
      rank++;
    }

    var tmp = [];
    var rank_start = Math.max(1, rank-n);
    for (var i = rank_start; i<=rank; i++) {
      var pair = {};
      pair.bib = bibs[i-1];
      pair.rank = i;
      tmp.push(pair);
    }
    res.bibs = tmp;
  }
  return JSON.stringify(res);
}
