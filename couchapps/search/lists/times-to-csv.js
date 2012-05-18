function(head, req) {

  start({
    "headers": {
      "Content-Type": "text/plain"
    }
  });

  var row;
  var race_id;
  // first doc contains the infos
  row = getRow()
  var start_times = row.value.start_times;

  while(row = getRow()) {
    // We get the race of the bib
    race_id = row.doc.course;

    row = getRow();
    for (var i=0; i<row.value.times.length; i=i+1) {
      var absolute_time = row.value.times[i] - start_times[race_id];
      var sec = Math.floor(absolute_time / 1000);
      var min = Math.floor(sec / 60);
      var hour = Math.floor(min / 60);
      var date_str = [hour, min % 60, sec % 60].map(function (n) {
        return ((n < 10) && (n >= 0) ? '0' : '') + n
      }).join(":");

      send([row.value.bib, date_str, row.value.site_id+1, i+1].join(";") + "\r\n");
    }
  }

  return
}
