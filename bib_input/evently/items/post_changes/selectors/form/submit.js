function() {
  var form = $(this)[0];
  var bib = form["bib"].value;
  var lap = form["lap"].value;
  var ts = form["ts"].value;
  if (bib == "" || ts == "" || lap == "" ) return false;
  bib = parseInt(bib);
  lap = parseInt(lap);

  var app = $$(this).app;
  if ((bib == app.current_bib) && (lap == app.current_lap)) {
    // $.log("ERASING current_li");
    app.current_li = null;
    app.current_bib = 0;
    app.current_lap = 0;
  }

  $(this).parents("li").hide('fast', function() {
    call_with_checkpoints(bib, app, function(checkpoints) {
      remove_checkpoint(checkpoints, ts);
      app.db.saveDoc(checkpoints);
    });
  });

  return false;
};
