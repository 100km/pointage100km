function(data) {
  var p;
  var app = $$(this).app;
  if (data[0] == undefined)
    return {
      item_0 : [],
      items : [],
    }

  if (data[0].value && (data[0].value.bib != app.current_bib || data[0].value.lap != app.current_lap) ) {
    app.current_li = $($("#items").find("li")[1]); // 1 because 0 is the table's title
    place_arrow($($("#items").find("li")[1]));
    app.current_bib = data[0].value.bib
    app.current_lap = data[0].value.lap
    $(this).trigger("change_infos");
  }

  function create_infos(r) {
    p = {};
    p.bib = r.value && r.value.bib;
    p.lap = r.value && r.value.lap;
    p.ts  = r.key[1];
    p.time_hour = time_to_hour_string(p.ts);

    p.nom = r.infos && r.infos.nom;
    p.prenom = r.infos && r.infos.prenom;
    p.course = r.infos && r.infos.course;

    return p;
  }

  // separate first element from others
  return {
    item_0 : [create_infos(data.shift())],
    items : data.map(create_infos)
  }
};
