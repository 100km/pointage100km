function(cb, x, data) {
  var app = $$(this).app;
  var bib = data.bib;
  if (bib == undefined) {
    cb({times:[], infos:data});
    return;
  }

  var race_id = data.race
  var max_lap = app.races_laps[race_id];
  app.db.view("search/all-times-per-bib-ng", {
    startkey: [bib,null],
    endkey: [bib,max_lap+1],
    inclusive_end: false,
    success: function(times) {
      cb({
        times: times,
        infos: data
      });
    }
  });
}
