function(data) {
  var p;
  var app = $$(this).app;
  if (data[0] == undefined)
    return {
      item_0 : [],
      items : [],
    }

  //$.log("In post_changes of items.");
  // we ensure that data is valid
  if (data[0].value && data[0].key) {
    // If no current_bib / current_lap. Or the one existing doesn't equals to the first one.
    if ( (app.current_bib == undefined || app.current_bib == 0 || app.current_lap == undefined || app.current_lap ==0)
        || (data[0].value.bib != app.current_bib || data[0].value.lap != app.current_lap || data[0].key[1] != app.current_ts) ) {
      // Update to the first one.
      //$.log("In post_changes of items : really change values to current_bib=" + data[0].value.bib + " current_lap="
      //      + data[0].value.lap + " current_ts=" + data[0].key[1]);
      // current_li will be dealed after rendering mustache in after.js
      app.current_bib = data[0].value.bib
      app.current_lap = data[0].value.lap
      app.current_ts = data[0].key[1]
    }
  }

  // Always trigger change_infos event so that infos are always up to date with database
  $(this).trigger("change_infos");

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

  return {
    items : data.map(create_infos)
  }
};
