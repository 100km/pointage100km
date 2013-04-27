function(cb) {
  var app = $$(this).app;

  if (!appinfo_initialized(app)) {
    cb({site_id: undefined, data: []});
    return;
  }

  var site_id = app.sites_nb - 1;

  db_recent(app, function(data) {
    cb({site_id: site_id, data: data});
  }, site_id, 5);
};

