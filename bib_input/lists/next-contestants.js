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
  var last_time = 0;
  if (searched_bib == undefined) {
        res.bibs = [];
  }
  else {
    while (row = getRow()) {
      var tmp_bib = row["value"]["bib"];
      var tmp_time = row["value"]["times"];
      var this_time = tmp_time[tmp_time.length - 1]
      bibs.push({bib:tmp_bib, time:this_time});
      if (tmp_bib == searched_bib) {
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
      if (i == rank_start) {
        time = bibs[i-1].time;
        // TODO use time_to_hour_string
        date = new Date(time)
        pair.time = date.getHours() + ":" + date.getMinutes() + ":" + date.getSeconds();
      }
      else {
        time = bibs[i-1].time - bibs[i-2].time;
        sec = parseInt(time / 1000);
        min = parseInt(sec / 60);
        hour = parseInt(min / 60);
        sec = sec % 60;
        min = min % 60;
        pair.time = "+ " + hour + "h" + min + "m" + sec + "s";
      }
      pair.rank = i;
      tmp.push(pair);
    }
    res.bibs = tmp;
  }
  return JSON.stringify(res);
}
