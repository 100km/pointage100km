function(head, req) {

  start({
    "headers": {
      "Content-Type": "text/plain"
    }
  });

  var row;
  var race_id;
  // first doc contains the infos
  row = getRow();
  var start_times = row.value.start_times;
  var nb_checkpoints = row.value.sites.length;

  send("INSERT INTO `race_checkpoint_times` (`race_description`, `bib`, `time`, `checkpoint_index`) VALUES\r\n");
  while(row = getRow()) {
    // We get the race of the bib
    if (! row.doc) {
      // Get next row to throw it
      row = getRow();
      continue;
    }

    race_id = row.doc.race;

    row = getRow();
    for (var i=0; i<row.value.times.length; i=i+1) {
      var absolute_time = row.value.times[i] - start_times[race_id];
      var sec = Math.floor(absolute_time / 1000);
      var min = Math.floor(sec / 60);
      var hour = Math.floor(min / 60);
      var date_str = "'" + [hour, min % 60, sec % 60].map(function (n) {
        return ((n < 10) && (n >= 0) ? '0' : '') + n
      }).join(":") + "'";
      var lap = i;

      // Special case for teams... (order was 566, 564, 565) change race_id and lap...
      // in 2016, there are 2 teams, order was 662, 663, 664 and 1159, 1160, 1161
      //if ((row.value.bib >= 662  && row.value.bib <= 664) ||
      //    (row.value.bib >= 1159 && row.value.bib <= 1161) )
      //  race_id = race_id - 1;
      //if (row.value.bib == 662 || row.value.bib == 1160)
      //  lap = 0;
      //if (row.value.bib == 663 || row.value.bib == 1159)
      //  lap = 1;
      //if (row.value.bib == 664 || row.value.bib == 1161)
      //  lap = 2;

      // race_description is hard-coded
      send("(" + [79 + race_id, row.value.bib, date_str, row.value.site_id + lap * nb_checkpoints].join(", ") + "),\r\n");
    }
  }

  return
}
