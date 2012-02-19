function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;
  var bib = app.current_bib;
  var lap = app.current_lap;
  var kms = site_lap_to_kms(app, site_id, lap).toFixed(2);

  call_with_previous(app, site_id, bib, lap, kms, cb);

};

