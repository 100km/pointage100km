function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;
  if (!appinfo_initialized(app))
    cb([]);
  db_recent(app, cb);
};

