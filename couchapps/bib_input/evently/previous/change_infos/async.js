function(cb, e, data) {
  var app = $$(this).app;
  db_previous(app, app.site_id, data, cb);
};

