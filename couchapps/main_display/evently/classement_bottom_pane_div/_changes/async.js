function(cb) {
  var app = $$(this).app;

  var site_id = app.sites_nb - 1;

  if (!appinfo_initialized_no_site(app)) {
    cb({site_id: site_id, data: []});
    return;
  }

  db_recent(app, function(data) {
    cb({site_id: site_id, data: data});
  }, site_id, 5, true);
};

