function() {
  var form = $(this);
  var fdoc = form.serializeObject();
  fdoc.created_at = new Date();
  $$(this).app.db.saveDoc(fdoc, {
    success : function() {
      form[0].reset();
    }
  });
  return false;
};
