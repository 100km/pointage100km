function(data) {
  var app = $$(this).app;

  var result = {};
  result.infos = data.infos;
  result.times = _.map(data.times.rows, function(time) {
    var res = {}
    var lap = time.key[1];
    var site_id = time.key[2];
    res.time = time_to_hour_string(time.value);
    res.site = app.sites[site_id];
    res.kms  = site_lap_to_kms(app, site_id, lap);
    res.lap  = lap;
    return res;
  });
  return result;
}
