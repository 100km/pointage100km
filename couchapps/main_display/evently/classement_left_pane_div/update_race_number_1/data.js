function(data) {
  var p = {};
  p.items = [];
  var i = 0;
  var app = $$(this).app;

  var start_time = app.start_times[data.rows[0].contestants[0].value.bib];

  while (data.rows[0].contestants[i]) {
    var item = {};
    var current_infos = data.rows[0].contestants[i].value;
    var current_contestant = app.contestants[current_infos.bib];
    var lap = - data.rows[0].contestants[i].key[1];

    //$.log("current_infos = " + JSON.stringify(current_infos));
    if (current_contestant === undefined) {
      $.log("skip contestant: " + data.bib);
    }

    item.rank    = i;
    item.dossard = current_infos.bib;
    item.kms     = site_lap_to_kms(app, current_infos.site_id, lap)
    item.nom     = current_contestant.nom;
    item.prenom  = current_contestant.prenom;
    item.time    = time_to_hour_string(current_infos.times[lap-1] - start_time);

    p.items.push(item);
    i++;
  }

  p.count = i;

  $.log("p: " + JSON.stringify(p));

  return p;
};
