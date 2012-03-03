function(cb, e, data) {
  var app = $$(this).app;
  var site_id = app.site_id;
  data = data || {};
  var bib = data.bib;
  var lap = data.lap;
  var ts = data.ts;

  call_with_previous(app, site_id, bib, lap, ts, cb);
};

