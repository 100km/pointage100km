function(head, req) {
  var row;
  var searched_bib = req.query["bib"];
  var n = req.query["n"] || 1;
  var bibs = [];
  var found;

  start({
    "headers": {
      "Content-Type": "text/plain"
    }
  });

  var res = {};
  if (searched_bib == undefined)
        res.error = "no bib";
  else {
    while (row = getRow()) {
      var tmp = row["value"]["bib"];
      if (tmp == searched_bib) {
        found = true;
        break;
      }
      bibs.push(tmp);
    }

    if (!found)
      res.error = "not found"
    else
      res.bibs = bibs.slice(bibs.length-n);
  }
  return JSON.stringify(res);
}
