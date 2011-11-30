function() {
  var form = $(this)[0];
  var fdoc = {};
  fdoc.bib  = form["bib"].value;
  fdoc.type = "contestant-checkpoint";
  var app = $$(this).app;

  app.db.view("bib_input/current-lap", {
                key: fdoc.bib,
                group: true,
                success: function(data) {
                   $.log("data is ");
                   $.log(data);
                   fdoc.lap = (data["rows"][0] && data["rows"][0]["value"] + 1) || 1;
                   $.log("extracted is");
                   $.log(fdoc.lap);
                   app.db.saveDoc(fdoc, {
                        success : function() {
                        form.reset();
                        }
                   });}});
  return false;
};
