function(data) {
  var p;
  var app = $$(this).app;
  if (data[0] == undefined)
    return {
      item_0 : [],
      items : [],
    }

  $.log("In post_changes for items");
  // If no current_bib / current_lap, take the first one
  if (! (app.current_bib && app.current_lap)) {
    if (data[0].value && (data[0].value.bib != app.current_bib || data[0].value.lap != app.current_lap) ) {
      $.log("In after of items change values");
      // current_li will be dealed after rendering mustache in after.js
      app.current_bib = data[0].value.bib
      app.current_lap = data[0].value.lap

      $(this).trigger("change_infos");
    }
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
    items : data.map(create_infos)
  }
};
