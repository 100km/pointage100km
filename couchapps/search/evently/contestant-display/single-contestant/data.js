function(data) {
  var app = $$(this).app;

  var result = {};
  result.infos = data.infos;
  result.times = [];
  for(var i= 0; i < data.times.length; i++) {
    for(var j= 0; j < data.times[i].times.length; j++) {
      result.times[j*3 + i] = {};
      result.times[j*3 + i].time = time_to_hour_string(data.times[i].times[j]);
      result.times[j*3 + i].site = app.sites[data.times[i].site_id];
      result.times[j*3 + i].kms  = site_lap_to_kms(app, data.times[i].site_id, j + 1);
      result.times[j*3 + i].lap  = j + 1;
    }
  }
  return result;
}
