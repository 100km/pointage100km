function() {
  var form = $(this)[0];
  var fdoc = {};
  fdoc.bib  = form["bib"].value;
  fdoc.type = "contestant-checkpoint";
  $$(this).app.db.saveDoc(fdoc, {
    success : function() {
      form.reset();
    }
  });
  return false;
};
