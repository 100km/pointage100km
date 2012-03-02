function(cb, e, data) {
  var app = $$(this).app;
  var site_id = app.site_id;
  $.log('in async change_infos event handler of previous widget');
  $.log(data);
  data = data || {};
  var bib = data.bib || app.current_bib;
  var lap = data.lap || app.current_lap;
  var ts = data.ts || app.current_ts;
  var kms = site_lap_to_kms(app, site_id, lap).toFixed(2);

  call_with_previous(app, site_id, bib, lap, ts, kms, cb);
};

