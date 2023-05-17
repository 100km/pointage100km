function(data) {
  var p = {};
  p.items = [];
  var i = 0;
  var app = $$(this).app;

  var race_id = data.race_id;
  var start_time = app.start_times[race_id];
  p.race_id = race_id;
  p.race_name = app.races_names[race_id];
  p.displayrank = (p.race_id != 3);

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
    item.bib     = current_infos.bib;
    item.kms     = site_lap_to_kms(app, current_infos.site_id, lap)
    item.time    = int_to_datestring(current_infos.times[current_infos.times.length - 1] - start_time);
    item.is_odd  = ((i%2) == 0);

    if (current_contestant === undefined) {
      $.log("skip contestant infos for bib " + current_infos.bib);
    } else {
      item.name         = current_contestant.name;
      item.first_name   = current_contestant.first_name;
      item.championship = current_contestant.championship;
    }

    p.items.push(item);
  }

  p.count = i;

  if (p.race_id == 3) { // 1 tour du soir
    p.items.sort(function(a,b) {
      return (
           a.name.localeCompare(b.name, "fr")
        || a.first_name.localeCompare(b.first_name, "fr")
      );
    });
  }

  if (p.items.length>10) {
    for (var j = 0; j<10; j++)
      p.items.unshift({});

    for (var j = 0; j<10; j++)
      p.items.push({});
  }

  //$.log("p: " + JSON.stringify(p));

  return p;
};
