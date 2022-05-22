function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;

  db_recent(app, function(data) {
    cb({site_id: site_id, data: data});
  }, site_id, 10, true);
};

