function(data) {
  var app = $$(this).app;

  data.current_bib = app.current_bib;
  data.current_lap = app.current_lap;
  data.nom = data.infos.nom;
  data.prenom = data.infos.prenom;

  if (data.avg_present) {
    var local_kms = app.kms_site[app.site_id][app.current_lap] - app.kms_site[data.last_site][data.last_lap];
    var local_time = data.bib_time - data.last_timestamp;
    // $.log("local_time=" + local_time + " local_kms=" + local_kms + " app.kms_site[app.site_id][app.current_lap]=" + app.kms_site[app.site_id][app.current_lap] + " app.current_lap=" + app.current_lap + " data.last_lap=" + data.last_lap + " data.last_timestamp=" + data.last_timestamp + " data.bib_time=" + data.bib_time);

    data.last_site_name = app.sites[data.last_site]
    data.local_kms = local_kms.toFixed(2);
    data.local_average = (local_kms * 1000 * 3600 / local_time).toFixed(2);
  }

  return data;
};
