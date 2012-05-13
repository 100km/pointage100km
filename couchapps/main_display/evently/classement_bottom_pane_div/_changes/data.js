function(data) {
  var p;
  var app = $$(this).app;
  if (data[0] == undefined) {
    return {
      item_0 : [],
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
    p.kms = site_lap_to_kms(app, 2, p.lap);

    if (p.lap == 5)
      p.style = "color:red; font-weight:bold;";

    p.nom = r.infos && r.infos.nom;
    p.prenom = r.infos && r.infos.prenom;
    p.race = r.infos && r.infos.course;

    var time_to_convert = p.ts - app.start_times[p.race];
    p.global_average = p.kms * 1000 * 3600 / time_to_convert;
    p.global_average = p.global_average.toFixed(2);

    p.race_name = app.races_names[p.race];
    return p;
  }

  return {
    items : data.map(create_infos)
  }

};
