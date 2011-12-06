function() {
  var form = $(this)[0];
  var bib = form["bib"].value;
  var lap = form["lap"].value;
  if (bib == "" || lap == "") return false;

  var app = $$(this).app;

  call_with_checkpoints(bib, app, function(checkpoints) {
    remove_checkpoint(checkpoints, lap);
    app.db.saveDoc(checkpoints);
  });
  return false;
};
