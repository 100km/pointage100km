function() {
  var form = $(this)[0];
  var bib = form["bib"].value;
  var ts = form["ts"].value;
  if (bib == "" || ts == "") return false;
  bib = parseInt(bib);

  var app = $$(this).app;
  $(this).parents("li").hide('fast', function() {
    call_with_checkpoints(bib, app, function(checkpoints) {
      remove_checkpoint(checkpoints, ts);
      app.db.saveDoc(checkpoints);
    });
  });

  return false;
};
