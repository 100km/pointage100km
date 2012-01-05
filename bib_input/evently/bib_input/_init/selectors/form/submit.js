function() {
  var form = $(this)[0];
  var bib = parseInt(form["bib"].value); // we are sure it's an integer because of the regexp check.
  if (! isBib(bib)) return false;
  form.reset();

  var app = $$(this).app;

  call_with_checkpoints(bib, app, function(checkpoints) {
    if (checkpoints["bib"] == undefined) {
      checkpoints = new_checkpoints(bib, app.site_id);
    }
    add_checkpoint(checkpoints);
    app.db.saveDoc(checkpoints);
  });
  return false;
};
