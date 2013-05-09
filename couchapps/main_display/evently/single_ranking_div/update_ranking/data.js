function(data) {
  var p = {};
  p.items = [];
  var first_cat = [[], []];
  var first_steen_found = [false, false];
  var i = 0;
  var app = $$(this).app;

  var race_id = data.race_id;
  var start_time = app.start_times[race_id];
  var text_gender = ["Premier homme ", "Première femme "];
  p.race_id = race_id;
  p.race_name = app.races_names[race_id];

  if (! data.data.rows[0])
    return p;

  while (data.data.rows[0].contestants[i]) {
    var item = {};
    var current_infos = data.data.rows[0].contestants[i].value;
    var current_contestant = app.contestants[current_infos.bib];
    var lap = - data.data.rows[0].contestants[i].key[1];

    // $.log("current_infos = " + JSON.stringify(current_infos));
    item.is_odd = i%2;
    i++;
    item.rank    = i;
    item.bib     = current_infos.bib;
    item.kms     = site_lap_to_kms(app, current_infos.site_id, lap)
    item.time    = int_to_datestring(current_infos.times[lap-1] - start_time);

    if (current_contestant === undefined) {
      $.log("skip contestant infos for bib " + current_infos.bib);
    } else {
      var gender_female = current_contestant.sex == "F" ? 1 : 0;

      item.name       = current_contestant.name;
      item.first_name = current_contestant.first_name;
      item.zipcode    = current_contestant.zipcode;
      item.city       = current_contestant.city;
      item.cat_name   = app.cat_names[current_contestant.cat];

      // default font
      item.font_weight = "normal";
      item.spec = "";
      if (first_cat[gender_female][current_contestant.cat] === undefined) {
        item.font_weight = "bold";
        item.spec = text_gender[gender_female] + item.cat_name;
        first_cat[gender_female][current_contestant.cat] = true;
      }

      if ((item.zipcode == "59181") && (first_steen_found[gender_female] == false)) {
        item.font_weight = "bold";
        if (item.spec != "")
          item.spec = item.spec + " et ";
        item.spec = item.spec + text_gender[gender_female] + "Steenwerckois";
        first_steen_found[gender_female] = true;
      }
    }

    p.items.push(item);
  }

  p.count = i;

  //$.log("p: " + JSON.stringify(p));

  return p;
};
