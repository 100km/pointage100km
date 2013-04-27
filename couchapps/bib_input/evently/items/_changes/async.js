function(cb) {
  var app = $$(this).app;
  if (!appinfo_initialized(app)) {
    cb([]);
    return;
  }
  var site_id = app.site_id;
  db_recent(app, cb, site_id, 10);
};

