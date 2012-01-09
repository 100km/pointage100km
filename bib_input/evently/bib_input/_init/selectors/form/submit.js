function() {
  var form = $(this)[0];
  var bib = form["bib"].value; // we are sure it's an integer because of the regexp check.
  if (! isBib(bib)) return false;
  bib = parseInt(bib);
  form.reset();

  var app = $$(this).app;
  app.current_bib = 0;
  app.current_lap = 0;

  call_with_race_id(bib, app, function(race_id) {
    call_with_checkpoints(bib, app, function(checkpoints) {
      if (checkpoints["bib"] == undefined) {
        checkpoints = new_checkpoints(bib, race_id, app.site_id);
      }
      add_checkpoint(checkpoints);
      app.db.saveDoc(checkpoints);
    });
  });
  return false;
};
