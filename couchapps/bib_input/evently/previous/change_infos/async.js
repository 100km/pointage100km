function(cb, e, data) {
  var app = $$(this).app;
  if (data.race == 5) { //5 is "relai course par equipe team"
    cb(data);
  } else {
    db_previous(app, app.site_id, data, cb);
  }
};

