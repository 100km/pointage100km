function(head, req) {
  // Next line is CouchApp directive
  // !code _attachments/lib/utils.js

  var searched_bib = req.query["bib"];
  var n = req.query["n"] || 0;
  var bib_lap = req.query["lap"];

  start({
    "headers": {
      "Content-Type": "text/plain"
    }
  });

  var row;
  var bibs = [];
  var rank = 1;
  var res = {};
  var last_time = 0;
  var bib_time = 0;
  if (searched_bib == undefined) {
        res.bibs = [];
  }
  else {
    while (row = getRow()) {
      var tmp_bib = row["value"]["bib"];
      var tmp_time = row["value"]["times"];
      var this_time = tmp_time[bib_lap-1];
      bibs.push({bib:tmp_bib, time:this_time});
      if (tmp_bib == searched_bib) {
        bib_time = this_time;
        break;
      }
      rank++;
    }

    var tmp = [];
    var rank_start = Math.max(1, rank-n);
    for (var i = rank_start; i<=rank; i++) {
      var pair = {};
      var time = 0;
      pair.bib = bibs[i-1].bib;
      if (i == rank)
        pair.time = time_to_hour_string(bib_time);
      else {
        time = bib_time - bibs[i-1].time;
        sec = parseInt(time / 1000);
        min = parseInt(sec / 60);
        hour = pad2(parseInt(min / 60)); // no need to take % 24 because the race lasts only for 24 hours.
        sec = pad2(sec % 60);
        min = pad2(min % 60);
        pair.time = "- " + hour + "h" + min + "m" + sec + "s";
      }
      pair.rank = i;
      tmp.push(pair);
    }
    res.bibs = tmp;
  }
  return JSON.stringify(res);
}
