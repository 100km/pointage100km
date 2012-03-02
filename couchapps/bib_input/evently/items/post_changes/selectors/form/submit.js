function() {
  var form = this;
  var bib = form.bib.value;
  var lap = form.bib.value;
  var ts = form.ts.value;
  if (bib == "" || ts == "" || lap == "" ) return false;
  bib = parseInt(bib);
  lap = parseInt(lap);

  // Hide the line and then delete with async query.
  var app = $$(this).app;
  $(this).parents("li").hide('fast', function() {
      submit_remove_checkpoint(bib, app, ts);
  });

  return false;
};
