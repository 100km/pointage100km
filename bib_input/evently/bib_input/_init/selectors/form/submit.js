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

  app.db.view("bib_input/current-lap", {
                key: fdoc.bib,
                group: true,
                success: function(data) {
                   fdoc.lap = (data["rows"][0] && data["rows"][0]["value"] + 1) || 1;
                   app.db.saveDoc(fdoc)
		}});
  return false;
};
