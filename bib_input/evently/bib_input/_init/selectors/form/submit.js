function() {
  var form = $(this)[0];
  var bib = form["bib"].value;
  if (! isBib(bib)) return false;
  form.reset();

  var app = $$(this).app;

  call_with_checkpoints(bib, app, function(checkpoints) {
    if (checkpoints["bib"] == undefined) {
        checkpoints = new_checkpoints(bib);
    }
    add_checkpoint(checkpoints);
    app.db.saveDoc(checkpoints);
  });
  return false;
};
