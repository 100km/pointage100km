function() {
  var form = $(this)[0];
  var bib = form["bib"].value; // we are sure it's an integer because of the regexp check.
  if (! isBib(bib)) return false;
  bib = parseInt(bib, 10);
  form.reset();

  var app = $$(this).app;
  app.current_bib = 0;
  app.current_lap = 0;

  submit_bib(bib, app);
  return false;
};
