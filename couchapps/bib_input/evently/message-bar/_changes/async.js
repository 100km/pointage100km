function(cb) {
  var app = $$(this).app;
  db_messages(app, cb, app.site_id);
}
