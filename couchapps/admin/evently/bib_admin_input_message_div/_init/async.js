function(cb) {
  var app = $$(this).app;

  app.db.openDoc('infos', {
    success: cb,
    error: cb,
  });
}
