function(cb, e, data) {
  var app = $$(this).app;
  var site_id = app.site_id;
  data = data || {};
  var bib = data.bib;
  var lap = data.lap;
  var ts = data.ts;
  var kms = site_lap_to_kms(app, site_id, lap).toFixed(2);

  call_with_previous(app, site_id, bib, lap, ts, kms, cb);
};

