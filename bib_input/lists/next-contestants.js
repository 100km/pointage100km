function(head, req) {
  // Next line is CouchApp directive
  // !code _attachments/lib/utils.js

  var searched_bib = req.query["bib"];
  var n = req.query["n"] || 0;
  var bib_lap = req.query["lap"];
  var start_time = req.query["start_time"];
  var kms = req.query["kms"];

  start({
    "headers": {
      "Content-Type": "application/json"
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
      var time_to_convert = 0;
      pair.bib = bibs[i-1].bib;
      pair.hour_time = time_to_hour_string(bibs[i-1].time); // this is the absolute hour
      if (i == rank) {
        time_to_convert = bib_time - start_time;
        prefix = "&nbsp; ";
      }
      else {
        time_to_convert = bib_time - bibs[i-1].time;
        prefix = "- ";
      }
      sec = parseInt(time_to_convert / 1000);
      min = parseInt(sec / 60);
      // we don't take % 24 in order to be coherent with global average
      // The race day we MUST have the correct start-time for each race.
      hour = pad2(parseInt(min / 60));
      sec = pad2(sec % 60);
      min = pad2(min % 60);
      pair.time = prefix + hour + ":" + min + ":" + sec;
      pair.rank = i;
      tmp.push(pair);
    }
    res.bibs = tmp;

    // calculates global average
    // time_to_convert shall have bib_time - start_time
    res.global_average = kms * 1000 * 3600 / time_to_convert;
    res.global_average = res.global_average.toFixed(2);
  }
  return JSON.stringify(res);
}
