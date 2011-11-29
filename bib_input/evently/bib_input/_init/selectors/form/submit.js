function() {
  var form = $(this)[0];
  var fdoc = {};
  fdoc.bib  = form["bib"].value;
  fdoc.type = "contestant-checkpoint";

  var res;
  $$(this).app.db.view("bib_input/contestant-checkpoints", {
                async : false,
                success: function(data) {
                   $.log("data is ");
                   $.log(data);
                   res = data;
                }});
  $.log("res is");
  $.log(res);

  $$(this).app.db.saveDoc(fdoc, {
    success : function() {
      form.reset();
    }
  });
  return false;
};
