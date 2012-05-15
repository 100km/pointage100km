function(head, req) {

  start({
    "headers": {
      "Content-Type": "text/plain"
    }
  });

  var row;
  while(row = getRow()) {
    for (var i=0; i<row.value.times.length; i=i+1) {
    var sec = Math.floor(row.value.times[i] / 1000);
    var min = Math.floor(sec / 60);
    var hour = Math.floor(min / 60);
    var date_str = [hour % 24, min % 60, sec % 60].map(function (n) {
      return ((n < 10) && (n >= 0) ? '0' : '') + n
    }).join(":");

    send([row.value.bib, date_str, row.value.site_id, i].join(";") + "\n");
    }
  }

  return
}
