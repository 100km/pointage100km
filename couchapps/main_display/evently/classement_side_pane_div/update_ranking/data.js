function(data) {
  var p = {};
  p.items = [];
  var i = 0;
  var app = $$(this).app;

  var race_id = data.race_id;
  var start_time = app.start_times[race_id];
  p.race_id = race_id;
  p.race_name = app.races_names[race_id];

  if (! data.data.rows[0])
    return p;

  while (data.data.rows[0].contestants[i]) {
    var item = {};
    var current_infos = data.data.rows[0].contestants[i].value;
    var current_contestant = app.contestants[current_infos.bib];
    var lap = - data.data.rows[0].contestants[i].key[1];

    //$.log("current_infos = " + JSON.stringify(current_infos));
    i++;
    item.rank    = i;
    item.dossard = current_infos.bib;
    item.kms     = site_lap_to_kms(app, current_infos.site_id, lap)
    item.time    = time_to_hour_string(current_infos.times[lap-1] - start_time);
    item.is_odd  = ((i%2) == 0);

    if (current_contestant === undefined) {
      $.log("skip contestant infos for bib " + current_infos.bib);
    } else {
      item.nom     = current_contestant.nom;
      item.prenom  = current_contestant.prenom;
    }

    p.items.push(item);
  }

  p.count = i;

  //$.log("p: " + JSON.stringify(p));

  return p;
};
