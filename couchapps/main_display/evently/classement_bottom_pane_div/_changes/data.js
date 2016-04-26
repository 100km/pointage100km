function(data) {
  var p;
  var app = $$(this).app;
  var site_id = data.site_id;
  data = data.data;

  var safe_site_name = "";
  if (app && app.sites && site_id<app.sites.length) {
    safe_site_name = app.sites[site_id];
  }

  if (data[0] == undefined || !appinfo_initialized_no_site(app)) {
    return {
      item_0 : [], //TODO unused?
      site_name: safe_site_name,
      items : [],
    }
  }

  // Return the data to display on item line.
  function create_infos(r) {
    p = {};
    p.bib = r.value && r.value.bib;
    p.lap = r.value && r.value.lap;
    p.ts  = r.key[1];
    p.time_hour = time_to_hour_string(p.ts);
    p.kms = site_lap_to_kms(app, site_id, p.lap);

    p.name = r.infos && r.infos.name;
    p.first_name = r.infos && r.infos.first_name;

    var race_id = r.infos && r.infos.race;
    p.race = race_id;

    if (p.lap == app.races_laps[race_id])
      p.style = "color:red; font-weight:bold;";


    var time_to_convert = p.ts - app.start_times[p.race];
    p.global_average = p.kms * 1000 * 3600 / time_to_convert;
    p.global_average = p.global_average.toFixed(2);

    p.race_name = app.races_names[p.race];
    return p;
  }

  return {
    items : data.map(create_infos),
    site_name: safe_site_name
  }

};
