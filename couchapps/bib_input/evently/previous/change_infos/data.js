function(data) {
  var app = $$(this).app;

  // If there is warning, nothing to do, display the widget.
  if (data.warning) {
    return data;
  }

  data.nom = data.infos.nom;
  data.prenom = data.infos.prenom;

  var tmp = [];
  var start_time = app.start_times[data.infos.course];

  for (var i = 0; i<data.limit; i++) {
    var pair = {};
    var time_to_convert = 0;
    var cur_pred = data.predecessors[i];
    if (cur_pred == undefined)
      break;
    var cur_time = cur_pred.value.times[data.lap - 1];
    pair.bib = cur_pred.value.bib;
    pair.hour_time = time_to_hour_string(cur_time); // this is the absolute hour
    if (i == 0) {
      time_to_convert = data.ts - start_time;
      prefix = "&nbsp; ";
      data.global_average = data.kms * 1000 * 3600 / time_to_convert;
      data.global_average = data.global_average.toFixed(2);
    }
    else {
      time_to_convert = data.ts - cur_time;
      prefix = "- ";
    }
    sec = parseInt(time_to_convert / 1000);
    min = parseInt(sec / 60);
    // we don't take % 24 in order to be coherent with global average
    // The race day we MUST have the correct start-time for each race.
    hour = pad2(parseInt(min / 60));
    sec = pad2(sec % 60);
    min = pad2(min % 60);
    pair.time = prefix + hour + ":" + min + ":" + sec;
    pair.rank = data.rank-i;
    tmp[data.limit - 1 - i] = pair;
  }

  data.bib_time = tmp.pop();
  data.bibs = tmp;


  if (data.average.avg_present) {
    var local_kms = site_lap_to_kms(app, app.site_id, data.lap) - site_lap_to_kms(app, data.average.last_site, data.average.last_lap);
    var local_time = data.ts - data.average.last_timestamp;

    data.last_site_name = app.sites[data.average.last_site]
    data.local_kms = local_kms.toFixed(2);
    data.local_average = (local_kms * 1000 * 3600 / local_time).toFixed(2);
  }

  return data;
};
