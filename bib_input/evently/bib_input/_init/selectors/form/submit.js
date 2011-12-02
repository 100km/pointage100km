function() {
  var form = $(this)[0];
  var bib = form["bib"].value;
  if (bib == "") return false;
  form.reset();

  var fdoc = {};
  fdoc.bib  = bib;
  fdoc.created_at = new Date();
  fdoc.type = "contestant-checkpoint";
  var app = $$(this).app;

  call_with_lap(fdoc.bib, app, function(lap) {
    fdoc.lap = lap;
    app.db.saveDoc(fdoc);
  });
  return false;
};
